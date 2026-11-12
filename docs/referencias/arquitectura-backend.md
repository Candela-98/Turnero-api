# Arquitectura Backend

## Proposito

Esta guia explica la arquitectura backend esperada para Turnero API durante el MVP.

Sirve para:

- Orientar a una desarrolladora junior.
- Revisar PRs con criterios consistentes.
- Dar contexto a Codex antes de pedir cambios de backend.

No reemplaza las fuentes de verdad del MVP:

- `../mvp/schema-db-mvp.md`
- `../mvp/api-contracts-mvp.md`
- `../mvp/flujos-funcionales-mvp.md`
- `../mvp/auth/google-mvp.md`
- `../mvp/plan-migracion-backend-mvp.md`

Para buenas practicas de codigo Java/Spring, usar tambien `buenas-practicas-java-spring.md`.

## Vision general

Turnero API usa Spring Boot con arquitectura en capas.

Objetivo:

- Mantener controllers simples.
- Concentrar reglas de negocio en services.
- Separar contratos HTTP de persistencia.
- Scopear datos por `business_id`.
- Evolucionar hacia PostgreSQL/Flyway sin depender de H2 como modelo real.
- Mantener PRs chicos y testeables.

Flujo general:

```text
HTTP request
  -> Controller
  -> Service interface
  -> Service implementation
  -> Repository
  -> Database
  -> Mapper
  -> Response DTO
```

## Capas

### Controller

Responsabilidad:

- Exponer endpoints REST.
- Recibir path params, query params y request bodies.
- Aplicar Bean Validation en DTOs.
- Delegar en services.
- Devolver status HTTP y response DTOs.

No debe:

- Consultar repositories directamente.
- Contener reglas de negocio.
- Calcular disponibilidad.
- Resolver transiciones de estado.
- Exponer entities JPA.
- Aceptar `business_id` desde requests admin.

### Service interface

Responsabilidad:

- Definir el contrato de negocio usado por controllers.
- Ocultar la implementacion concreta.
- Favorecer Dependency Inversion y tests.

Reglas:

- Controllers dependen de interfaces.
- Interfaces deben ser chicas y orientadas a casos de uso.
- No agregar metodos futuros "por si acaso".

### Service implementation

Responsabilidad:

- Ejecutar reglas de negocio.
- Coordinar repositories y mappers.
- Validar scoping por negocio.
- Controlar transacciones.
- Calcular campos controlados por backend.
- Lanzar excepciones de dominio o aplicacion.

Ejemplos:

- Calcular `ends_at`, `duration_minutes` y `price_cents` de appointments.
- Validar que un staff ofrece un servicio.
- Validar solapamientos.
- Centralizar transiciones de estado.
- Buscar o crear customer en booking publico.

### Repository

Responsabilidad:

- Acceso a datos.
- Queries por ID y `business_id`.
- Queries para filtros, availability, solapamientos y tokens.

Reglas:

- En endpoints admin, las consultas deben estar scopeadas por el business actual.
- Recursos de otro negocio deben responder como no encontrados cuando aplique.
- Evitar N+1 en listados y availability.

### Entity

Responsabilidad:

- Representar persistencia JPA.
- Reflejar tablas, relaciones y restricciones del schema MVP.

Reglas:

- Alinearse con `../mvp/schema-db-mvp.md`.
- No exponerse directamente desde controllers.
- No reemplazar DTOs.
- No depender de identificadores externos de Google.

### DTO

Responsabilidad:

- Definir contrato HTTP.
- Separar entrada y salida.
- Evitar campos internos o controlados por backend en requests.

Reglas:

- JSON y query params usan `snake_case`.
- Requests admin no aceptan `business_id`.
- Requests no aceptan `created_at`, `updated_at`, snapshots, status o source si son controlados por backend.
- Responses no exponen datos internos fuera del contrato.

### Mapper

Responsabilidad:

- Convertir entre DTOs y entities.
- Mantener conversiones simples y legibles.

No debe:

- Ejecutar queries.
- Resolver reglas de negocio complejas.
- Decidir transiciones de estado.
- Calcular availability.

## Flujo de request admin

Ejemplo para un endpoint admin:

```text
POST /api/v1/appointments
  -> AppointmentController recibe AppointmentCreateRequestDTO
  -> Bean Validation valida campos basicos
  -> Controller delega en AppointmentService
  -> Service obtiene business actual desde contexto/session
  -> Service valida customer, staff, service y staff-service
  -> Service calcula snapshots y valida solapamiento
  -> Repository persiste appointment scopeado por business
  -> Mapper arma AppointmentResponseDTO
  -> Controller responde 201 Created
```

Reglas:

- El frontend admin nunca envia `business_id`.
- El business se resuelve desde la sesion autenticada; los tests pueden reemplazar el contexto de forma explicita.
- La validacion informativa de availability no reemplaza la validacion transaccional al crear o editar.

## Contexto de negocio y auth

### Contexto autenticado vigente

Los endpoints admin obtienen usuario y business desde la cookie de sesion validada por `AdminAuthInterceptor`. `AuthenticatedCurrentBusinessContext` implementa `CurrentBusinessContext`, por lo que los services permanecen desacoplados del transporte HTTP.

Reglas:

- No hardcodear business en services de dominio.
- No aceptar `business_id` desde requests admin.
- Reemplazar el contexto sólo de forma explicita en tests.
- Toda nueva familia de endpoints admin debe agregarse a la proteccion y probar `401`, `403` y scoping.

### Auth real

Cuando se implemente Google Auth:

- Google valida identidad.
- Turnero crea sesion propia.
- La cookie HTTP-only autentica requests admin.
- `users.business_id` define el scope del negocio.
- Roles MVP: `OWNER`.

El dominio operativo debe depender de `users.id` y `business_id`, no de Google `sub`.

## Persistencia

PostgreSQL es la DB objetivo.

Flyway es la fuente versionada del schema.

Reglas:

- No usar `ddl-auto` como reemplazo de migraciones.
- La migracion inicial debe representar `../mvp/schema-db-mvp.md`.
- Seeds dev no contienen datos reales.
- Indices y constraints deben acompanar consultas y reglas principales.
- Tests rapidos pueden usar H2 si no dependen de PostgreSQL.
- Usar PostgreSQL/Testcontainers para Flyway, constraints y concurrencia.

## Contratos HTTP

Los contratos viven en `../mvp/api-contracts-mvp.md`.

Reglas arquitectonicas:

- Endpoints nuevos o migrados salen bajo `/api/v1`.
- DTOs v1 definen el contrato externo.
- Responses no devuelven entities.
- Errores usan formato MVP.
- Query params usan `snake_case`.
- Recursos fuera de scope se manejan sin revelar datos de otro negocio.

## Errores

El manejo de errores se centraliza en `GlobalExceptionHandler`.

Reglas:

- Services lanzan excepciones de dominio/aplicacion.
- Controllers no construyen errores complejos manualmente.
- Errores inesperados no exponen detalles internos al cliente.
- Validaciones de DTO devuelven `VALIDATION_ERROR`.
- Conflictos de negocio usan `409` cuando corresponda.

Codigos frecuentes:

- `VALIDATION_ERROR`
- `NOT_FOUND`
- `CONFLICT`
- `SLOT_UNAVAILABLE`
- `STAFF_SERVICE_MISMATCH`
- `INVALID_STATE_TRANSITION`
- `UNAUTHORIZED`
- `FORBIDDEN`

## Logging y observabilidad

Usar SLF4J/Logback para eventos de aplicación. Reservar los logs para datos operativos y aplicar la misma política a todos los niveles, incluidos `DEBUG` y `TRACE`. Los health checks básicos se exponen mediante Actuator según el perfil.

### Datos excluidos de los logs

- ID tokens de Google, valores de `Cookie`, `Set-Cookie` y `Authorization`, tokens de sesión y tokens públicos, incluidos los de cancelación. Excluir también `session_token_hash`, hashes de tokens públicos y claves del limitador.
- Credenciales de conexión, contraseñas, claves y otros secretos de configuración. Mantener estos valores fuera de los mensajes, las causas y las trazas de excepciones.
- Datos personales y texto libre, como nombres, emails, teléfonos, notas de reserva y motivos de cancelación. Excluir IP y `User-Agent` de los eventos de auth, cleanup y rate limiting. Incorporar identificadores de negocio o de recursos únicamente cuando exista una necesidad operativa explícita.
- Cuerpos completos de requests o respuestas, cabeceras crudas, URLs con tokens o parámetros sensibles, entidades completas y valores enlazados a sentencias SQL. Mantener los mismos datos fuera de etiquetas de métricas, analytics y trazas. Usar valores ficticios en fixtures y pruebas.

### Datos útiles para el diagnóstico

- Correlacionar respuesta y log con `X-Request-Id`. `RequestIdFilter` acepta un único UUID con el formato esperado, lo normaliza o genera uno nuevo, lo copia al MDC para la duración de la solicitud y lo limpia al finalizar. El identificador sirve para correlación, sin intervenir en autenticación ni en las cuotas del limitador.
- Usar el tipo de evento, el código HTTP o de aplicación, el tipo de excepción, la duración y los conteos cuando aporten contexto. En el error inesperado manejado por el advice, el evento contiene el tipo de excepción y el request ID. El texto, la causa y el stack trace quedan fuera del log.
- Para errores de persistencia, conservar el `SQLState`. Por ejemplo, `23505` permite diagnosticar una violación de unicidad sin inspeccionar el valor de la clave. Si hace falta identificar una operación HTTP, usar el patrón de ruta o una ruta sanitizada.

### Protecciones y evidencia de TURN-116

| Protección | Alcance verificado |
| --- | --- |
| Representación textual segura de `GoogleLoginRequestDto` y `PublicAppointmentResponseDto` | El primero redacta el ID token en `toString()`. El segundo excluye `cancelToken` de su representación textual. Los tests HTTP activaron `TRACE` en los conversores MVC de entrada y salida, comprobaron que los eventos se emitieron y verificaron que los tokens ficticios quedaron fuera del log. El contrato JSON conserva `id_token` y `cancel_token`. [AuthControllerTest](../../src/test/java/com/turnero/api/controller/AuthControllerTest.java) · [PublicBookingControllerTest](../../src/test/java/com/turnero/api/controller/PublicBookingControllerTest.java) |
| Rechazo de un ID token malformado | El verificador real recibe un token ficticio de un solo segmento y falla durante el parseo, antes de consultar claves externas. La prueba HTTP comprueba `401 UNAUTHORIZED`, ausencia de cookie de sesión en la respuesta y ausencia del valor ficticio en el cuerpo y el log. [RequestIdFilterTest](../../src/test/java/com/turnero/api/config/RequestIdFilterTest.java) |
| Detalle de errores JDBC | El bloque versionado de Hikari en `application.yml` fija `logServerErrorDetail=false` para `dev`, `desa` y `prod`. La prueba con PostgreSQL 16 y Testcontainers cargó el perfil `prod` y solo aportó los datos de conexión del contenedor. Al provocar una violación de unicidad, la captura conservó `SQLState: 23505` y excluyó el hash ficticio de sesión. [Configuración Spring](../../src/main/resources/application.yml) · [JdbcSensitiveLoggingPostgresIT](../../src/test/java/com/turnero/api/integration/JdbcSensitiveLoggingPostgresIT.java) |
| Correlación del error inesperado y sanitización de rutas | La prueba de `500` comprueba el request ID de la respuesta en la misma línea del evento de error y verifica que los valores ficticios de la solicitud, la excepción y su causa queden fuera del log. El advice MVC y el fallback servlet `/error` sanitizan el campo `path` de la respuesta. Una ruta de cancelación reconocida se representa como `/api/v1/public/cancellations/{token}` y una ruta ambigua como `/{redacted}`. La prueba de fallback cubre un despacho `ERROR` de Tomcat con `404`. [RequestIdFilterTest](../../src/test/java/com/turnero/api/config/RequestIdFilterTest.java) · [PublicErrorFallbackHttpTest](../../src/test/java/com/turnero/api/exception/PublicErrorFallbackHttpTest.java) |

La validación local del backend del 12 de noviembre de 2026 tuvo 65 pruebas focalizadas aprobadas. La suite completa de Gradle con Docker tuvo 804 aprobadas, 0 fallidas y 5 omitidas por la condición de ejecución del laboratorio de planes de consulta.

El sanitizador comprueba la ruta antes y después de normalizarla. Cubre prefijos de contexto, escapes, parámetros de matriz, segmentos adicionales y variantes con `.` y `..`. Acota la decodificación anidada a cinco pasadas. El campo `path` sanitizado del JSON protege la respuesta HTTP y no modifica la URL que reciben otros componentes.

### Logs externos pendientes

Queda pendiente verificar la configuración efectiva y las capturas de logs del BFF, el balanceador, el servidor HTTP y el servidor PostgreSQL. Usar tráfico ficticio para revisar que los access logs, métricas y trazas excluyan URLs de cancelación con el token, cabeceras y cuerpos sensibles. Revisar también el detalle y los parámetros en los logs de errores del servidor PostgreSQL. La evidencia anterior corresponde a la respuesta HTTP y a la captura del proceso Java, incluida la salida JDBC de Hibernate.

## Testing arquitectonico

Cada PR debe incluir tests proporcionales al riesgo.

Tipos esperados:

- Unit tests para reglas de negocio.
- Service tests para scoping, validaciones y transiciones.
- Controller tests para status HTTP y contrato JSON.
- Integration tests para flujos que cruzan capas.
- Testcontainers para Flyway, constraints, PostgreSQL y concurrencia.

Coverage:

- Mantener minimo 80% global.
- Priorizar assertions utiles sobre coverage nominal.

## Uso con Codex

Cuando se use Codex para backend, incluir esta guia como contexto.

El prompt siguiente es un ejemplo historico de TURN-33. Reemplazar siempre la clave, el alcance y las fuentes por los de la subtarea activa; Jira y el tracking son la referencia de trabajo vigente.

Prompt recomendado:

```text
Estamos en Turnero API. Trabaja solo sobre TURN-33.
Lee docs/referencias/arquitectura-backend.md,
docs/referencias/buenas-practicas-java-spring.md,
docs/mvp/plan-migracion-backend-mvp.md y docs/mvp/schema-db-mvp.md.
Implementa solo el alcance de la subtarea.
No agregues endpoints ni auth.
Mantene ./gradlew test verde y explica que verificaste.
```

Prompt a evitar:

```text
Implementa la arquitectura completa del backend y todos los endpoints del MVP.
```

Reglas:

- Una subtarea Jira por PR.
- No mezclar capas o features fuera del alcance.
- No cambiar contratos sin actualizar tests y docs fuente.
- No revertir cambios ajenos.

## Checklist rapido de arquitectura

Antes de aprobar un PR:

- Controller delega en service interface.
- Service implementation contiene reglas de negocio.
- Repository no contiene decisiones funcionales complejas.
- DTOs no exponen entities.
- Requests admin no aceptan `business_id`.
- Queries admin estan scopeadas por business.
- Errores pasan por handler global.
- Logs no filtran datos sensibles.
- Tests cubren reglas nuevas.
- `./gradlew test` pasa.
