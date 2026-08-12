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
- El business se resuelve desde contexto dev al inicio y desde sesion autenticada despues.
- La validacion informativa de availability no reemplaza la validacion transaccional al crear o editar.

## Contexto de negocio y auth

### Contexto dev temporal

Antes de Google Auth, endpoints admin usan un contexto dev/test temporal.

Objetivo:

- Permitir implementar y probar agenda admin localmente.
- Mantener el contrato correcto desde el inicio.
- Evitar que el frontend envie `business_id`.

Reglas:

- No hardcodear business en services de dominio.
- Aislar el contexto para poder reemplazarlo en auth.
- No tratarlo como auth real.

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
- `UNAUTHENTICATED`
- `FORBIDDEN`

## Logging y observabilidad

Reglas:

- Usar request id.
- Usar SLF4J/Logback.
- No usar `System.out.println`.
- No loguear cookies, session tokens, Google ID tokens, public cancellation tokens ni secrets.
- Health checks basicos se exponen con Actuator segun perfil.

Los logs deben ayudar a diagnosticar sin filtrar datos sensibles.

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
