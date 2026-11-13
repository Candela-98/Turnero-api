# Entorno local del backend MVP

## Requisitos

- Windows con PowerShell 7 para los ejemplos de esta guía, JDK 21 y el Gradle Wrapper del repositorio. La primera ejecución puede necesitar acceso a la distribución de Gradle y a las dependencias.
- Docker Desktop en modo de contenedores Linux y Docker Compose para levantar PostgreSQL 16 o ejecutar las pruebas con Testcontainers. El arranque aislado requiere Docker Compose **2.24.4 o posterior** para usar `!override`. El recorrido de Windows se validó con **Docker Compose v5.1.4**. [Requisito de `!override` en Docker Compose](https://docs.docker.com/reference/compose-file/merge/#replace-value)
- Trabajar desde la raíz de un clon o worktree de `Turnero-api`. El smoke público usa el seed de `dev` y datos ficticios.

```powershell
java -version
.\gradlew.bat --version
docker info
docker compose version
```

## Arranque en PowerShell

El perfil `dev` usa `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` y `DB_PASSWORD`. Compose interpola sus variables desde el entorno o `.env` y Spring, al ejecutar el wrapper directamente, toma las variables del entorno del proceso. Cambiar `.env` para Compose no exporta variables a PowerShell.

El ejemplo usa un proyecto Compose, un contenedor y un volumen propios, con PostgreSQL y el backend accesibles por loopback. El Compose versionado asigna el nombre fijo `turnero-postgres`. El override temporal evita colisionar con esa instancia y publica el puerto de la prueba solo en `127.0.0.1`.

En una consola, verificar que el contenedor y los volúmenes de la prueba no existan y que los puertos `55432` y `18080` estén libres. Si alguno está en uso, elegir otros identificadores y puertos antes de continuar.

```powershell
docker ps -a --filter 'name=^/turn115-runbook-postgres$' --format '{{.Names}} {{.Status}}'
docker volume ls --filter 'label=com.docker.compose.project=turn115-runbook' --format '{{.Name}}'
Get-NetTCPConnection -State Listen -LocalPort 55432,18080 -ErrorAction SilentlyContinue
```

En esa misma consola, generar la contraseña desechable una vez para este volumen. Conservar las variables y el archivo temporal hasta finalizar.

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '55432'
$env:DB_NAME = 'turnero_runbook'
$env:DB_USERNAME = 'turnero_runbook'
$env:DB_PASSWORD = [guid]::NewGuid().ToString('N')

$override = New-TemporaryFile
@(
    'services:'
    '  postgres:'
    '    container_name: turn115-runbook-postgres'
    '    ports: !override'
    '      - "127.0.0.1:55432:5432"'
) | Set-Content -LiteralPath $override.FullName
$compose = @('-p', 'turn115-runbook', '-f', 'docker-compose.yml', '-f', $override.FullName)

docker compose @compose up -d --wait postgres
docker compose @compose ps
docker compose @compose exec -T postgres pg_isready -U $env:DB_USERNAME -d $env:DB_NAME
.\gradlew.bat --no-daemon bootRun --args='--spring.profiles.active=dev --server.address=127.0.0.1 --server.port=18080'
```

Esperar `healthy` y `accepting connections` para PostgreSQL, y el mensaje de arranque de Spring en la consola. Para verificar el historial Flyway con los mismos parámetros de Compose, detener el backend con `Ctrl+C` después del smoke y ejecutar la consulta de la sección siguiente en esta consola. También se puede consultar mientras corre el backend desde otra consola que tenga las mismas variables y el arreglo `$compose`.

### Variables de autenticación y datos sensibles

El smoke público puede ejecutarse directamente por HTTP local. Para validar el flujo admin hace falta aprovisionar un OWNER de prueba y configurar `GOOGLE_CLIENT_ID`. Si el login pasa por el frontend, ese cliente usa el mismo ID web mediante `NEXT_PUBLIC_GOOGLE_CLIENT_ID`. Consultar la [guía de Google Auth y aprovisionamiento](../mvp/auth/google-mvp.md#configuración-de-google) para `GOOGLE_OWNER_SUB` y `GOOGLE_OWNER_EMAIL` y el [contrato de cookie por ambiente](../mvp/api-contracts-mvp.md#cookie-por-ambiente) para sus overrides. El flujo de ID token del MVP prescinde de `GOOGLE_CLIENT_SECRET`.

Mantener `DB_PASSWORD`, credenciales, cookies y tokens fuera de ejemplos, historial, capturas y logs. Seguir la [política de logging y observabilidad](arquitectura-backend.md#logging-y-observabilidad).

## Flyway, health y readiness

Flyway corre al arrancar Spring. `dev` y `desa` cargan las migraciones estructurales y el seed local, con `out-of-order` habilitado para bases que ya aplicaron el seed. `prod` carga las migraciones estructurales. Hibernate valida el esquema después de Flyway. En `test` con H2 se usa `create-drop` y Flyway queda desactivado. Las pruebas de PostgreSQL configuran su propia base y activan o invocan Flyway cuando lo requieren.

Consultar el historial después de que el backend haya arrancado al menos una vez sobre ese volumen.

```powershell
docker compose @compose exec -T postgres psql -U $env:DB_USERNAME -d $env:DB_NAME -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

En el volumen `dev` nuevo de esta revisión se aplicaron `1, 3, 4, 1000, 1001, 1002, 1003, 1004` con `success = t`. En bases existentes, `installed_rank` refleja el orden efectivo.

Con el backend corriendo, verificar en otra consola.

```powershell
$base = 'http://127.0.0.1:18080'
$health = Invoke-WebRequest -SkipHttpErrorCheck "$base/actuator/health"
$health.StatusCode
[Text.Encoding]::UTF8.GetString($health.RawContentStream.ToArray()) | ConvertFrom-Json
(Invoke-WebRequest -SkipHttpErrorCheck "$base/actuator/health/readiness").StatusCode
```

En el arranque local con `dev`, health devolvió HTTP `200` con `status=UP` y sin desglose de componentes. Los bloques `management` versionados están bajo `spring` y no configuran las propiedades raíz de Actuator. `/actuator/health/readiness` devolvió `404`. Para comprobar que el backend está listo en local, combinar health con el historial de Flyway y la lectura de `booking-profile` del smoke. La configuración efectiva de health y del probe en despliegue queda pendiente del plan AWS.

## Smoke de reserva y cancelación

Usar el seed `barber-studio` de `dev` y la consola de las consultas HTTP. El seed habilita confirmación manual y exige cancelar más de tres horas antes del inicio. El ejemplo usa la zona horaria del negocio y elige un turno que supere ese límite por al menos diez minutos para completar el smoke. Si no hay un turno elegible, ampliar el rango dentro de la ventana de reserva o consultar otro servicio.

Consultar las reglas y los errores en el [contrato de booking público](../mvp/api-contracts-mvp.md#public-booking) y el [contrato de cancelación](../mvp/api-contracts-mvp.md#public-cancellation-token). El smoke usa las formas comprobadas en el backend.

| Sección del contrato | Diferencia comprobada |
| --- | --- |
| `GET /api/v1/public/businesses/{business_slug}/services` | El ejemplo del contrato usa `data`. La API devuelve `services`. |
| `GET /api/v1/public/businesses/{business_slug}/availability` | El ejemplo usa `date` y un objeto con `slots`. La API recibe `from` y `to` y devuelve la lista de slots directamente. Los horarios devueltos son locales y se serializan sin offset. |
| `POST /api/v1/public/businesses/{business_slug}/appointments` | El ejemplo de respuesta anida el turno en `appointment` y el token en `cancellation.token`. El `201` comprobado devuelve `appointment_id` y `cancel_token` en un objeto plano. El smoke envía el horario local devuelto por disponibilidad. |
| `GET /api/v1/public/cancellations/{token}` | El ejemplo muestra `starts_at` y `ends_at` con offset. El resumen comprobado devuelve las horas locales del turno sin offset. |

```powershell
$booking = "$base/api/v1/public/businesses/barber-studio"
$profile = Invoke-RestMethod "$booking/booking-profile"
$catalog = Invoke-RestMethod "$booking/services"
$service = $catalog.services[0].id

$zone = [TimeZoneInfo]::FindSystemTimeZoneById($profile.business.timezone)
$today = [TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $zone).Date
$from = $today.AddDays(1).ToString('yyyy-MM-dd')
$to = $today.AddDays(4).ToString('yyyy-MM-dd')
$slots = Invoke-RestMethod "$booking/availability?from=$from&to=$to&service_offering_id=$service&staff_member_id=any"
$now = [TimeZoneInfo]::ConvertTimeFromUtc([datetime]::UtcNow, $zone)
$cancelCutoff = $now.AddHours(3).AddMinutes(10)
$slot = $slots | Where-Object { [datetime]$_.starts_at -gt $cancelCutoff } | Select-Object -First 1
if (!$slot) { throw 'No hay slots con margen para cancelar en el rango del seed' }

$reservation = @{
    service_offering_id = $service
    staff_member_id = [string]$slot.available_staff_member_ids[0]
    starts_at = $slot.starts_at
    customer = @{ name = 'Cliente QA'; email = 'turn115@example.invalid'; phone_number = '2025550143' }
} | ConvertTo-Json -Depth 4
$created = Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $reservation -StatusCodeVariable createCode "$booking/appointments"
$createCode
$created.status
[bool]$created.cancel_token
```

Esperar `201`, `PENDING` y `True`. Mantener `cancel_token` en la variable de la sesión y evitar mostrar el objeto completo o la URL de cancelación.

```powershell
$cancelUrl = "$base/api/v1/public/cancellations/$($created.cancel_token)"
$preview = Invoke-RestMethod -StatusCodeVariable previewCode $cancelUrl
if (!$preview.can_cancel) { throw 'La reserva ya no admite cancelación' }
$cancelBody = @{ cancellation_reason = 'Prueba local' } | ConvertTo-Json
$cancelled = Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $cancelBody -StatusCodeVariable cancelCode $cancelUrl
$again = Invoke-WebRequest -Method Post -ContentType 'application/json' -Body $cancelBody -SkipHttpErrorCheck $cancelUrl

$previewCode
$preview.can_cancel
$cancelCode
$cancelled.status
$again.StatusCode
($again.Content | ConvertFrom-Json).code
```

Esperar consulta `200` con `can_cancel=True`, cancelación `200` con `CANCELLED` y reutilización `409 TOKEN_USED`. Para ver el turno desde el lado admin, usar un OWNER de prueba y el [contrato de agenda autenticada](../mvp/api-contracts-mvp.md#appointments-admin). Para poblar agenda adicional, usar la [guía de seed de turnos locales](seed-agenda-local.md).

### Cierre de la prueba aislada

Detener `bootRun` con `Ctrl+C` y ejecutar desde la consola que conserva `$compose`. Usar `--volumes` únicamente para el proyecto desechable creado en esta guía.

```powershell
docker compose @compose down --volumes
Remove-Item -LiteralPath $override.FullName
```

## Pruebas en Windows

Gradle tiene una sola tarea `test`. La selección rápida es un filtro positivo de paquetes con unitarios y pruebas Spring/H2 que no requieren Docker. El filtro omite `integration`, donde conviven casos H2 y PostgreSQL. Usar `*Test` como filtro incluiría `FlywayMigrationTest` y requeriría Docker.

```powershell
.\gradlew.bat test --tests 'com.turnero.api.service.*' --tests 'com.turnero.api.controller.*' --tests 'com.turnero.api.repository.*' --tests 'com.turnero.api.config.*' --tests 'com.turnero.api.ratelimit.*'
```

Para la suite general, iniciar Docker y comprobar que `docker info` responde. Testcontainers levanta su propio PostgreSQL, de modo que la base de Compose puede estar detenida. La suite cubre entre otros casos Flyway, concurrencia y cleanup. El laboratorio `MvpQueryPlanPostgresIT` es optativo y se omite salvo con `TURNERO_QUERY_PLAN_AUDIT=true`. Sus escenarios viven en la [guía de medición de consultas](mvp-query-plan-measurement.md).

```powershell
docker info
.\gradlew.bat test
```

Esperar `BUILD SUCCESSFUL` y revisar las clases ejecutadas u omitidas en `build/test-results/test`, incluidas `FlywayMigrationTest` y `PublicTokenCleanupPostgresIT`.

## Cleanup y límites operativos

- Sesiones, configuración y observación en [Cleanup operativo de sesiones](../mvp/auth/google-mvp.md#cleanup-operativo-de-sesiones-turn-110).
- Cuotas, respuestas `429` y `503` y dependencias de despliegue en [Rate limiting del booking público](../mvp/rate-limiting-publico.md).
- Datos excluidos y verificación pendiente de logs externos en la [política de logging](arquitectura-backend.md#logging-y-observabilidad).

### Cleanup de tokens públicos

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `PUBLIC_TOKEN_CLEANUP_RETENTION_DAYS` | `30` | Retención antes del borrado. |
| `PUBLIC_TOKEN_CLEANUP_BATCH_SIZE` | `500` | Máximo global de candidatos por ejecución. |
| `PUBLIC_TOKEN_CLEANUP_CRON` | `0 0 3 * * *` | Ejecuta a las 03:00 según la zona del scheduler de la JVM. |

El job usa `public-token.cleanup`. Calcula el corte por zona de negocio y compara estrictamente con `used_at` si el token fue usado, o con `expires_at` si sigue sin usar. Recorre los negocios por ID, comparte un único presupuesto de lote y revalida la elegibilidad al borrar. V4 agrega los índices parciales de limpieza.

Para observar el disparo, en una prueba aislada fijar temporalmente el cron antes de arrancar el backend.

```powershell
$env:PUBLIC_TOKEN_CLEANUP_CRON = '0 * * * * *'
```

En la consola de `bootRun`, esperar `Public token cleanup completed` con `deletedCount` y `durationMs`. Cero borrados confirma ejecución en un seed sin candidatos. La prueba `PublicTokenCleanupPostgresIT` verifica borrado, retención y lotes con fixtures. Al terminar, quitar el override en esa consola con `Remove-Item Env:PUBLIC_TOKEN_CLEANUP_CRON` o cerrarla.

## Make y Colima

En un shell compatible con el [Makefile](../../Makefile), los atajos existentes usan `.env`, el Compose versionado y el perfil `dev` por defecto. Usarlos para el entorno local habitual, con sus nombres y puertos configurados.

```bash
make db-up
make db-logs
make run
make test
make db-down
```

`make db-logs` sigue el log de PostgreSQL. `make test` corre la suite general. En macOS, si el contexto es `colima` o empieza con `colima-`, el target configura el socket para Testcontainers. Para ejecutar Gradle directamente en ese contexto, usar el equivalente existente.

```bash
DOCKER_HOST="$(docker context inspect "$(docker context show)" --format '{{ .Endpoints.docker.Host }}')" \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew test
```

`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` permite que Ryuk monte el socket interno de la VM.

## Diagnóstico rápido

| Síntoma | Comprobación |
| --- | --- |
| `docker info` falla o Testcontainers no detecta Docker | Revisar `docker context ls` y un `DOCKER_HOST` heredado que esté apuntando a otro daemon. Iniciar Docker Desktop o Colima. En Windows, para el contexto `desktop-linux` validado, usar el named pipe de Docker Desktop y el socket interno si Ryuk lo necesita. |
| PostgreSQL no conecta | Revisar `docker compose @compose ps`, `docker compose @compose logs --tail 100 postgres` y `Test-NetConnection 127.0.0.1 -Port 55432`. Verificar que Spring y Compose reciban las mismas variables. Un volumen existente conserva las credenciales con las que se inicializó. |
| Spring falla al arrancar | Revisar la primera causa en la consola de `bootRun`. Distinguir conexión, error de migración Flyway y validación de esquema de Hibernate. Para un checksum o una migración pendiente, cotejar `flyway_schema_history` y los SQL versionados antes de modificar un SQL aplicado, ejecutar `repair` o borrar un volumen. |
| El smoke no ofrece slots o rechaza la cancelación | Comprobar que el negocio y el servicio estén activos y usar la zona devuelta por el perfil. Elegir otro slot con margen para el aviso de cancelación. Ante `429`, respetar `Retry-After`. |

### Docker Desktop con un contexto heredado incorrecto

Si `docker context ls` confirma que el motor Linux de Docker Desktop está en ese named pipe, usar las variables siguientes en la consola de pruebas. El valor de `DOCKER_HOST` debe coincidir con el endpoint que muestre tu contexto.

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
$env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = '/var/run/docker.sock'
docker info
```

## Referencia de despliegue AWS

El [plan de migración](../mvp/plan-migracion-backend-mvp.md#perfiles-spring) y el [tracking](../mvp/tracking-implementacion-mvp.md#pendientes-importantes-en-paralelo) mencionan `plan-deploy-aws-mvp.md`. La revisión base de TURN-115 no contiene ese archivo. Queda pendiente confirmar su ubicación y el probe de despliegue aplicable.
