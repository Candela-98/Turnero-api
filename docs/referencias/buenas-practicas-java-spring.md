# Buenas Practicas Java y Spring Boot

## Proposito

Esta guia define buenas practicas para implementar y revisar cambios backend en Turnero API.

Esta pensada para:

- Una desarrolladora junior que implementa PRs del MVP.
- Quien revise PRs.
- Pedidos de cambios con Codex u otras herramientas asistidas.

No reemplaza las fuentes de verdad del MVP:

- `../mvp/schema-db-mvp.md`
- `../mvp/api-contracts-mvp.md`
- `../mvp/flujos-funcionales-mvp.md`
- `../mvp/auth/google-mvp.md`
- `../mvp/plan-migracion-backend-mvp.md`

Para arquitectura backend, flujo de request y responsabilidades por capa, usar tambien `arquitectura-backend.md`.

## Principios base

- Aplicar SOLID, DRY, KISS y Clean Code.
- Respetar el dominio de Turnero: negocio, agenda, servicios, profesionales, clientes, booking y sesiones.
- Mantener PRs chicos, enfocados y revisables.
- No mezclar infraestructura, schema, auth y features en el mismo PR salvo que la subtarea lo indique.
- Evitar refactors no relacionados con el objetivo del PR.
- Preferir nombres claros sobre comentarios explicativos.
- Escribir codigo simple antes que abstracciones prematuras.
- Mantener `./gradlew test` verde al cerrar cada PR.

## SOLID en el proyecto

### Single Responsibility

Cada clase debe tener una razon clara para cambiar.

- Controller: HTTP, status codes, request/response y delegacion.
- Service: reglas de negocio y coordinacion transaccional.
- Repository: acceso a datos.
- Mapper: conversion entre DTOs y entidades.
- DTO: contrato de entrada/salida.
- Entity: persistencia y relaciones del modelo.

Evitar:

- Controllers con reglas de negocio.
- Mappers que validan reglas de dominio complejas.
- Services que construyen respuestas HTTP.
- Repositories con decisiones funcionales que deberian vivir en services.

### Open/Closed

El codigo debe permitir cambios razonables sin romper contratos existentes.

- Usar enums para estados controlados.
- Centralizar transiciones de estado de appointments.
- Encapsular integraciones externas, como Google Auth, detras de interfaces.

### Liskov Substitution

Las implementaciones deben respetar el contrato de sus interfaces.

- No cambiar semantica segun implementacion.
- No devolver `null` si la interfaz promete resultado obligatorio.
- No lanzar excepciones inesperadas fuera del contrato del service.

### Interface Segregation

Las interfaces deben ser especificas.

- Preferir interfaces chicas por caso de uso o recurso.
- No crear interfaces enormes con metodos que algunas implementaciones no pueden cumplir.
- No forzar metodos inutiles solo para reutilizar una abstraccion.

### Dependency Inversion

Las capas de alto nivel dependen de contratos, no de detalles.

- Controllers dependen de interfaces de services.
- Services pueden depender de interfaces para integraciones externas o puertos de dominio.
- Implementaciones concretas quedan detras de interfaces.

## Interfaces

En este proyecto se usa un criterio estricto de interfaces, siempre respetando SOLID.

Usar interfaces para:

- Services usados por controllers.
- Casos de uso con contrato claro.
- Integraciones externas, por ejemplo Google, email futuro o storage futuro.
- Componentes que puedan tener implementacion real, fake o mock en tests.
- Puertos de dominio donde importe desacoplar regla de negocio de detalle tecnico.

No usar interfaces para:

- DTOs.
- Entities JPA.
- Enums.
- Exceptions simples.
- Config classes.
- Mappers MapStruct, salvo necesidad concreta fuera del patron generado.
- Helpers privados sin contrato de negocio.

Reglas:

- La interfaz debe nombrar el contrato de negocio, no el detalle tecnico.
- La implementacion debe tener un nombre explicito, por ejemplo `AppointmentServiceImpl`.
- No agregar metodos "por si acaso".
- Si una interfaz empieza a crecer demasiado, dividirla por responsabilidad.
- No romper KISS: una interfaz debe mejorar el desacoplamiento, testabilidad o legibilidad.

## DRY, KISS y Clean Code

### DRY

No duplicar reglas de negocio.

Ejemplos:

- Una sola logica para validar scoping por `business_id`.
- Una sola logica para transiciones de estado de appointments.
- Una sola logica para calcular snapshots de appointment cuando corresponda.

No confundir DRY con abstraccion prematura:

- Dos bloques parecidos pueden quedarse separados si representan reglas distintas.
- Extraer helpers solo cuando el nombre mejora la lectura y evita duplicacion real.

### KISS

Elegir la solucion mas simple que cumpla el contrato.

- No sumar patrones si el PR no los necesita.
- No hacer generalizaciones para features post-MVP.
- No crear frameworks internos.
- Mantener queries y services entendibles para una dev junior.

### Clean Code

- Usar nombres expresivos.
- Mantener metodos cortos y con una intencion clara.
- Evitar booleanos ambiguos en APIs internas.
- Evitar efectos colaterales ocultos.
- Preferir early return cuando simplifique la lectura.
- Agregar comentarios solo para decisiones no obvias.

## Capas backend

### Controllers

Responsabilidades:

- Exponer endpoints.
- Validar DTOs con Bean Validation.
- Recibir path/query params.
- Delegar en services.
- Devolver status codes y DTOs de respuesta.

No deben:

- Consultar repositories directamente.
- Resolver reglas de dominio.
- Calcular disponibilidad.
- Resolver scoping manual si existe contexto/service para eso.
- Exponer entities JPA.

### Services

Responsabilidades:

- Reglas de negocio.
- Validaciones funcionales.
- Transacciones.
- Scoping por negocio.
- Coordinacion entre repositories.
- Calculos controlados por backend.

Ejemplos:

- Validar que staff ofrece un servicio.
- Validar que no hay solapamiento.
- Calcular `ends_at`, `duration_minutes` y `price_cents`.
- Resolver business desde contexto/session, no desde request.

### Repositories

Responsabilidades:

- Acceso a datos.
- Queries por ID y `business_id`.
- Queries necesarias para filtros, availability y conflictos.

Reglas:

- En endpoints admin, preferir queries scopeadas por `business_id`.
- Para recursos de otro negocio, responder como no encontrado cuando aplique.
- Evitar N+1 en listados o availability.

### DTOs

Responsabilidades:

- Definir el contrato HTTP.
- Separar request de response.
- Evitar exponer detalles internos del modelo.

Reglas:

- Requests admin no aceptan `business_id`.
- Requests no aceptan campos controlados por backend.
- Responses usan `snake_case` segun contrato.
- Usar DTOs especificos por profundidad: summary, response, create request, update request.

### Mappers

Responsabilidades:

- Convertir DTOs a entities y entities a DTOs.
- Mantener conversiones simples.

No deben:

- Ejecutar queries.
- Validar reglas de negocio complejas.
- Calcular disponibilidad.
- Decidir estados de dominio.

## Java

- Usar Java 21.
- Usar `BigDecimal` o enteros para dinero segun contrato; en MVP se usa `price_cents`.
- Usar enums para estados controlados.
- Evitar `double` para precios.
- Evitar `null` cuando el contrato requiere valor.
- Usar `Optional` solo como tipo de retorno cuando ayuda a expresar ausencia.
- No usar `Optional` en fields de entities o DTOs.
- Preferir `private final` para dependencias inyectadas.
- Usar constructor injection.
- Evitar clases utilitarias globales si una responsabilidad de dominio puede vivir en un service.

## Spring Boot

- Usar annotations de Spring con responsabilidad clara.
- `@RestController` para controllers HTTP.
- `@Service` para implementaciones de services.
- `@Repository` cuando corresponda.
- `@Transactional` en services que escriben o coordinan operaciones atomicas.
- No hardcodear configuracion de ambiente.
- Usar perfiles `dev`, `test`, `desa` y `prod` segun el plan.
- Usar Bean Validation en DTOs de request.
- Centralizar errores en `GlobalExceptionHandler`.

Evitar:

- `@Autowired` en fields.
- Configuracion sensible hardcodeada.
- Logica de negocio en configuration classes.
- Dependencias de Spring innecesarias en clases de dominio simples.

## JPA, Flyway y PostgreSQL

- PostgreSQL es la DB objetivo.
- Flyway es la fuente versionada del schema.
- Las entidades deben alinearse con `../mvp/schema-db-mvp.md`.
- No usar `ddl-auto` como reemplazo de migraciones.
- No guardar datos reales en seeds.
- No versionar dumps.
- Usar constraints e indices segun el schema y consultas reales.
- Validar con PostgreSQL/Testcontainers cuando importe comportamiento especifico de DB.

Buenas practicas:

- Nombrar tablas y columnas de forma consistente con el schema.
- Evitar relaciones lazy que generen N+1 en responses.
- No devolver entities directamente desde controllers.
- Cuidar transacciones en creacion de appointments y booking publico.

## Contratos HTTP

- Endpoints nuevos o migrados salen bajo `/api/v1`.
- JSON y query params usan `snake_case`.
- Fechas completas usan ISO-8601 con offset o UTC.
- Horas sin fecha usan `HH:mm`.
- Fechas sin hora usan `YYYY-MM-DD`.
- IDs se exponen como numeros.
- El backend controla `business_id`, `created_at` y `updated_at`.
- El cliente admin nunca envia `business_id`.
- Los endpoints publicos resuelven negocio por `business_slug`.

Antes de implementar un endpoint:

1. Revisar `../mvp/api-contracts-mvp.md`.
2. Revisar el flujo correspondiente en `../mvp/flujos-funcionales-mvp.md`.
3. Revisar el schema en `../mvp/schema-db-mvp.md`.
4. Confirmar el alcance de la subtarea Jira.

## Validaciones y errores

- Validaciones de formato y campos obligatorios viven en DTOs con Bean Validation.
- Validaciones de negocio viven en services.
- Errores se devuelven con el formato de `../mvp/api-contracts-mvp.md`.
- Errores inesperados no exponen stacktrace ni detalles internos.
- Recursos fuera del `business_id` actual deben responder como no encontrados cuando convenga no revelar existencia.

Codigos de error relevantes:

- `VALIDATION_ERROR`
- `UNAUTHENTICATED`
- `FORBIDDEN`
- `NOT_FOUND`
- `CONFLICT`
- `SLOT_UNAVAILABLE`
- `STAFF_SERVICE_MISMATCH`
- `INVALID_STATE_TRANSITION`
- `TOKEN_INVALID`
- `TOKEN_EXPIRED`
- `TOKEN_USED`

## Testing y coverage

Objetivo minimo:

- Mantener 80% de coverage global.
- Services y reglas criticas deben tener tests fuertes, no solo coverage nominal.

Cada PR debe incluir tests cuando cambia comportamiento.

Tipos de tests:

- Unit tests para reglas de negocio.
- Service tests para validaciones funcionales y scoping.
- Controller tests para status HTTP, body y contrato JSON.
- Integration tests para flujos que atraviesan capas.
- PostgreSQL/Testcontainers para Flyway, constraints, concurrencia y comportamiento especifico de DB.

Casos que siempre merecen tests:

- Scoping por `business_id`.
- Validaciones de DTOs.
- Transiciones de estado.
- Solapamiento de appointments.
- Availability.
- Auth/session.
- Tokens publicos.
- Errores esperados.

No alcanza con:

- Testear solo happy path.
- Verificar solo status HTTP sin body cuando el contrato importa.
- Subir coverage ejecutando codigo sin assertions utiles.

## Logging, seguridad y datos sensibles

- Usar SLF4J/Logback.
- No usar `System.out.println` en codigo productivo.
- Usar request id para trazabilidad.
- Loguear eventos utiles para troubleshooting.
- No loguear datos sensibles.

No loguear:

- Cookies.
- Session tokens.
- Google ID tokens.
- Tokens publicos de cancelacion.
- Secrets.
- Passwords si existieran en el futuro.

Errores:

- Loguear errores inesperados del lado servidor.
- No devolver stacktrace al cliente.
- No exponer mensajes internos innecesarios.

## Trabajo con Codex

Cuando se pida un cambio a Codex, incluir:

- Jira key.
- Objetivo del PR.
- Alcance exacto.
- Fuera de alcance.
- Docs fuente que debe leer.
- Tests esperados.
- Comando de verificacion.

El prompt siguiente es un ejemplo historico de TURN-33. Reemplazar siempre la clave, el alcance y las fuentes por los de la subtarea activa; Jira y el tracking son la referencia de trabajo vigente.

Prompt recomendado:

```text
Estamos en Turnero API. Trabaja solo sobre TURN-33.
Lee docs/mvp/plan-migracion-backend-mvp.md y docs/mvp/schema-db-mvp.md.
Implementa PostgreSQL local, Flyway y schema MVP completo segun la subtarea.
No agregues endpoints ni auth.
Mantene ./gradlew test verde y explica que verificaste.
```

Prompt a evitar:

```text
Implementa todo el backend MVP con auth, agenda, booking y deploy.
```

Reglas para usar Codex:

- No pedir cambios fuera del PR.
- No mezclar schema, auth y features salvo que la subtarea lo pida.
- Pedir que respete contratos existentes.
- Pedir que no revierta cambios ajenos.
- Pedir tests nuevos o actualizados.
- Revisar el diff antes de aceptar el resultado.

## Checklist de review

Antes de aprobar un PR:

- El PR cumple una sola subtarea Jira.
- No hay refactors no relacionados.
- `./gradlew test` pasa.
- Coverage global se mantiene en al menos 80%.
- Hay tests para reglas nuevas o modificadas.
- Controllers no contienen reglas de negocio.
- Services no devuelven detalles HTTP.
- DTOs no exponen entities ni campos internos.
- Endpoints v1 usan `snake_case`.
- Requests admin no aceptan `business_id`.
- Queries admin respetan scoping por business.
- No hay logs de tokens, cookies ni secrets.
- Errores siguen el contrato MVP.
- El PR describe que se probo.
