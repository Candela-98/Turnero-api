# Turnos propios y titularidad explícita — TURN-121

La identidad autenticada tiene un user_id global. Customer contiene la ficha del negocio.
La titularidad de cada turno se guarda en appointments.owner_user_id.
El listado exige conjuntamente el negocio del slug y ese titular explícito.

## Migración y datos existentes

V1006__appointment_customer_ownership.sql se agrega después de V1005 de TURN-120.
Se verificó la disponibilidad de V1006 en la base de TURN-120, origin/develop y las referencias
locales antes de crear el archivo. La base de trabajo es el commit 7c75d9a de TURN-120.

Todos los turnos anteriores reciben owner_user_id=NULL. No hay backfill por ficha, email,
teléfono, source, rol o sesión. Los caminos actuales de reserva invitada y alta admin también
mantienen NULL. Vincular Customer durante un login conserva ese resultado.

fk_appointments_owner_user referencia users(id). fk_appointments_owner_profile referencia
customers(business_id,id,user_id), cuya clave única se agrega en la misma migración.
Con MATCH SIMPLE, los turnos sin titular continúan admitidos. Con titular informado, PostgreSQL
exige que ficha, identidad y negocio correspondan. La titularidad impide desvincular esa ficha
o reasignar el turno a una ficha incompatible sin resolver previamente la operación explícita.

El índice parcial idx_appointments_customer_owner sirve al filtro por negocio y titular y al
orden predeterminado. El esquema conserva los turnos y referencias históricos existentes.

## Contrato de consulta

GET /api/v1/customer/businesses/{slug}/appointments?page=0&size=50&sort=starts_at,desc

Requiere la cookie cliente configurada en TURN-120 y una sesión de ámbito CUSTOMER.
Una identidad OWNER con sesión CUSTOMER puede consultar su propia ficha del negocio.

| Parámetro | Contrato |
| --- | --- |
| page | Desde 0, default 0 |
| size | Entre 1 y 100, default 50 |
| sort | starts_at,asc o starts_at,desc, default descendente |
| Desempate | id ASC para ambos sentidos |

Los parámetros repetidos, vacíos, inválidos o ajenos a ese conjunto reciben 400 VALIDATION_ERROR.
La identidad y el negocio se obtienen del contexto autenticado.
El listado incluye todas las fechas y estados con titularidad explícita, con paginación.

~~~json
{
  "data": [
    {
      "id": 123,
      "service_offering": { "id": 4, "name": "Corte" },
      "staff_member": { "id": 8, "name": "Profesional" },
      "starts_at": "2026-11-25T15:00:00-03:00",
      "ends_at": "2026-11-25T15:30:00-03:00",
      "status": "CONFIRMED"
    }
  ],
  "page": {
    "number": 0,
    "size": 50,
    "total_elements": 1,
    "total_pages": 1
  }
}
~~~

Se reutiliza CustomerPageResponseDto.PageInfo y las convenciones de límite, offset y orden
de la consulta administrativa. CustomerAppointmentReadQuery valida sólo los parámetros del
listado cliente. El COUNT y la página usan el mismo filtro en una transacción de sólo lectura
REPEATABLE_READ. El offset se calcula como long.

El DTO cliente proyecta únicamente los campos del ejemplo. Omite contactos, notas del cliente,
notas internas, motivos de cancelación/bloqueo, precios, tokens e IDs de ficha/titular.
Servicio y profesional se unen por ID y business_id. Se conservan referencias retiradas
lógicamente. Para una referencia histórica inconsistente con el negocio, id y name se devuelven
como NULL. Los nombres reflejan los registros conservados del catálogo.
starts_at y ends_at usan el offset de la zona IANA del negocio, igual que la lectura existente.

| Resultado | Respuesta |
| --- | --- |
| Sesión válida sin turnos propios | 200 con data vacía y totales en cero |
| Página posterior al final | 200 con data vacía y total de turnos propios |
| Cookie ausente, inválida, vencida, revocada o ámbito incorrecto | 401 |
| Sesión de otro negocio o negocio inactivo | 403 |
| Slug inexistente con sesión presente | 404 |
| Query inválida | 400 |

Las respuestas exitosas usan Cache-Control: no-store. El BFF deberá admitir esta ruta GET
y reenviar la cookie cliente al integrar la vista de Mis turnos.

## Validación compartida con TURN-120

CustomerSessionCookie extrae la cookie cliente con las mismas reglas y mensajes anteriores.
CustomerSessionResolver comparte la comprobación de scope CUSTOMER, vigencia, revocación,
customer_id, user_id y negocio del slug.

auth/me y el listado exigen negocio ACTIVE. Logout conserva la posibilidad de revocar en un
negocio inactivo, el 204 idempotente y la expiración de cookie ante una sesión ya inválida.
Login conserva la verificación externa de Google antes del bloqueo de escritura de Customer.
La extracción no cambia el camino de resolución o aprovisionamiento durante ese login.

El listado sólo lee. El bloqueo por negocio de TURN-120 continúa reservado a sus escrituras.

## Ediciones y convivencia con admin

AppointmentMapper ignora ownerUserId al crear desde el DTO admin. Las ediciones sobre la entidad
existente conservan ese campo. Un intento de cambiar customerId en un turno con titularidad
recibe 409 INVALID_STATE_TRANSITION antes de modificar campos. La misma ficha sigue siendo
válida en una edición. Los turnos con titular NULL conservan la reasignación administrativa.

La FK protege también las escrituras SQL incompatibles. Los cambios de horario, servicio,
profesional, notas o estado no adjudican ni transfieren titularidad.

## Reserva autenticada — TURN-122 / B3

POST `/api/v1/customer/businesses/{slug}/appointments` requiere la cookie CUSTOMER configurada
por TURN-120. Obtiene negocio, ficha y titular exclusivamente de `CustomerSessionResolver`.
Una identidad OWNER puede reservar con sesión CUSTOMER; la cookie ADMIN no autentica este POST.

### Decisión provisional pendiente de validación con el líder

La vinculación o creación de ficha permanece en el login de TURN-120: vínculo estable primero;
sin vínculo, una coincidencia única de email Google verificado sobre ficha libre, o creación
si no hay coincidencias. No se vincula por teléfono ni se adjudica historial anterior.

TURN-122 reutiliza esa distribución y no vuelve a resolver contactos. Una sesión CUSTOMER válida
ya identifica una ficha vinculada. El POST revalida negocio, vínculo, estado y bloqueo sobre esa
ficha después de adquirir los locks; si el vínculo no corresponde, rechaza sin buscar ni crear
otra ficha. Esta decisión fue autorizada provisionalmente para implementar y probar; **no está
aprobada por el líder y requiere su validación antes de integrar el PR**.

### Request y respuesta

~~~json
{
  "service_offering_id": 4,
  "staff_member_id": "8",
  "starts_at": "2026-12-02T15:00:00",
  "customer_notes": "Nota opcional"
}
~~~

`staff_member_id` acepta un ID como cadena o `"any"`, como la reserva invitada. `starts_at`
conserva la fecha/hora local del negocio del contrato de creación existente, sin offset.
Mis turnos devuelve el horario con el offset de la zona IANA del negocio.

Sólo esos cuatro campos están permitidos. Campos adicionales, incluidos `owner_user_id`,
`user_id`, `customer_id`, `business_id`, `customer` y variantes camelCase, reciben
`400 VALIDATION_ERROR`. El cuerpo no puede seleccionar titular ni ficha ni fijar estado,
precio, duración, fin o source. La restricción es propia del nuevo DTO; no altera el contrato
invitado ni la configuración global de JSON.

Responde `201 Created`, `Cache-Control: no-store`, con el mismo DTO de creación pública:
`appointment_id`, `service_offering_id`, `staff_member_id`, `starts_at`, `ends_at`,
`duration_minutes`, `price_cents`, `status` y `cancel_token`.
El token conserva la cancelación pública existente. Mis turnos sigue omitiéndolo.

| Condición | Respuesta |
| --- | --- |
| Sesión ausente, inválida, vencida, revocada o de ámbito incorrecto | 401 UNAUTHORIZED |
| Sesión de otro negocio o ficha trasladada fuera de ese negocio | 403 FORBIDDEN |
| Vínculo de ficha con identidad incompatible | 401 UNAUTHORIZED |
| Negocio inactivo o reserva pública deshabilitada | 403 FORBIDDEN |
| Ficha bloqueada | 403 CUSTOMER_BOOKING_BLOCKED, sin motivo privado |
| Slug o recurso inexistente/fuera del negocio | 404 NOT_FOUND |
| Cuerpo, ventana o anticipación inválidos | 400 VALIDATION_ERROR |
| Slot no disponible | 409 CONFLICT, como la reserva pública |

### Reutilización, atomicidad y concurrencia

`PublicBookingServiceImpl` implementa también `CustomerAppointmentBookingService`. Ambos puntos
de entrada transaccionales delegan al mismo núcleo: referencias, disponibilidad, ventana,
anticipación, profesional específico/any, snapshots, confirmación manual, turno y token.
Sólo cambia la resolución de ficha: contactos del invitado o ficha ya autenticada.
El núcleo recibe el negocio ya resuelto: del contexto CUSTOMER para el autenticado, y de la
búsqueda por slug para el invitado. No vuelve a elegir el negocio para crear la reserva.

El orden es profesional → negocio → ficha, compatible con la reserva invitada existente.
Se revalida disponibilidad después del lock del profesional. La ficha se refresca después de
los locks de negocio y ficha para observar cambios de vínculo, archivo o bloqueo. La sesión
se refresca y valida nuevamente tras la espera, incluyendo expiración y revocación.

Una ficha INACTIVE no bloqueada se restaura como en el flujo invitado. Una ficha bloqueada
no se restaura. La restauración y la creación del turno/token pertenecen a la transacción del
POST; cualquier fallo posterior las revierte. Los datos de identidad, vínculo y sesión ya
confirmados en el login se conservan. El POST no escribe nuevos vínculos ni crea fichas.

El turno se guarda con `business_id` y `customer_id` del contexto y
`owner_user_id=session.userId` antes del INSERT. No se guarda primero como invitado para
adjudicarlo después. Las FK de V1005/V1006 ya cubren la coherencia; no se agrega migración.
`source` conserva `PUBLIC_BOOKING` y no participa en la autorización de lectura.

El endpoint invitado siempre entrega titular NULL, incluso con cookie cliente o ficha vinculada.
Los turnos heredados e invitados permanecen fuera de Mis turnos. No hay backfill.

El BFF deberá admitir el POST y reenviar la cookie cliente, con la frontera de procedencia/CSRF
existente; su integración no forma parte de este cambio backend.

## Comprobaciones

CustomerAppointmentReadPostgresIT prueba vacío, paginación, desempates, fechas/estados,
dos identidades, dos negocios, scopes, expiración, logout, conflictos de reasignación y rollback,
privacidad, referencias históricas y exclusión de invitados vinculados posteriormente.
FlywayMigrationTest verifica desde V1005 la conservación del contenido anterior, las sesiones
legacy y la ausencia de titularidad tanto antes como después de vincular fichas.
Las regresiones HTTP de TURN-120 verifican la extracción compartida de sesión.


## Validación automatizada del 20/11/2026

- Primera corrida focalizada con 101 aprobadas, cero fallas/errores y cero omisiones.
- Suite completa con 864 aprobadas, cero fallas/errores y cinco omisiones optativas de
  MvpQueryPlanPostgresIT. Total registrado de 869 casos.
- CustomerAppointmentReadPostgresIT terminó con 37 casos aprobados, incluyendo los cuatro
  cambios de estado y la inserción concurrente entre COUNT y página bajo REPEATABLE_READ.
- FlywayMigrationTest terminó con diez casos aprobados. Las regresiones de TURN-120,
  lectura administrativa y edición de turnos quedaron incluidas en la suite completa.

Se ejecutó Java 21, Gradle 8.14.2 y PostgreSQL 16 con Testcontainers.
JAVA_TOOL_OPTIONS=-XX:+UseSerialGC se aplicó al proceso local, como en TURN-120.
Los resultados de la ejecución están en build/reports/tests/test/index.html.
El recorrido de creación autenticada y consulta posterior corresponde a TURN-122.

## Validación de TURN-122 del 25/11/2026

Implementación preparada sobre `55720c1f1445420d9ac6ae3e60571ce92caea93b` de TURN-121,
en `feature/customer-authenticated-booking-v1`. Sin migraciones nuevas. La regla provisional
indicada arriba continúa pendiente de validación con el líder antes de integrar el PR.

- Suite completa: 928 casos, 923 aprobados, cero fallas/errores y cinco omisiones de la auditoría
  optativa `MvpQueryPlanPostgresIT`.
- `CustomerAppointmentBookingPostgresIT`: 46 casos aprobados. Incluye 201 seguido de Mis turnos,
  dos cuentas y dos negocios, campos prohibidos, cuerpos inválidos, sesiones, referencias,
  contactos ambiguos sin nueva resolución, bloqueos, restauración, invitados e invalidación
  de datos cargados antes de esperar el lock.
- `AppointmentConcurrencyPostgresIT`: 27 casos aprobados, incluidos once nuevos de reserva
  autenticada contra cliente/invitado/admin, solapamientos parciales y rollback del ganador inicial.
- Dos casos unitarios nuevos verifican que el refresh observa un vínculo incompatible o bloqueo
  y no intenta resolver contactos ni restaurar la ficha.
- El rollback se prueba después de restaurar la ficha e insertar turno con titular y token,
  provocando una violación real de unicidad en PostgreSQL. Ficha, identidad y sesión del login
  confirmado permanecen; la restauración y la reserva fallidas se revierten.
- Cobertura JaCoCo global: 96,78% de líneas y 87,52% de ramas.
- `git diff --check` sin errores.

Entorno: Java 21, Gradle 8.14.2, PostgreSQL 16 con Testcontainers y
`JAVA_TOOL_OPTIONS=-XX:+UseSerialGC`, como en las dependencias. Se ejecutó Gradle con
`--offline --no-daemon test`; `jacocoTestReport` se ejecuta como finalizador del task test.
El reporte local está en `build/reports/tests/test/index.html` y la cobertura en
`build/reports/jacoco/test/html/index.html`.

## Revisión de TURN-122 contra 55720c1

Se retiró una segunda resolución por slug dentro del núcleo de creación: el POST autenticado
ahora pasa directamente el negocio del contexto validado. Una prueba unitaria verifica que
negocio, ficha y titular provienen de ese contexto y que no se consulta la resolución invitada.
El flujo público mantiene su búsqueda por slug y sus validaciones anteriores.

La revisión confirmó el orden profesional → negocio → ficha, los refresh posteriores a los
locks, la transacción externa que cubre restauración/turno/titular/token y la titularidad NULL
del invitado. El test de rollback compara además las filas completas de usuario y sesión
confirmadas en el login. El test de logs cubre éxito, campos rechazados y error inesperado,
verificando que no se imprimen cookie, token de cancelación, email, notas ni mensaje sensible
de la excepción. No se agregaron loggers ni configuración de logging.

La validación de la distribución login/POST con el líder continúa pendiente antes de integrar;
esta revisión técnica no constituye su aprobación.

Validación posterior a la revisión: 197 pruebas afectadas aprobadas, cero fallas,
errores u omisiones, con Java 21, SerialGC y PostgreSQL 16/Testcontainers. Incluye creación
autenticada, núcleo público, controller público, concurrencia, fichas/bloqueos, auth y Mis turnos.
La suite completa de 923 aprobadas y cinco omisiones anterior queda registrada arriba; esta
corrida focalizada valida la corrección y los dos casos nuevos de la revisión.
