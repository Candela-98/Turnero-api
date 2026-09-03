# Turnero API - Plan de Migracion Backend MVP

## Proposito

Este documento define como migrar el backend actual hacia el MVP aprobado, con PRs chicos, revisables y aptos para una desarrolladora junior inicial.

No reemplaza las fuentes de verdad:

- `schema-db-mvp.md`: tablas, campos, relaciones, enums y decisiones de persistencia.
- `api-contracts-mvp.md`: endpoints, DTOs, query params, errores, scoping y convenciones HTTP.
- `flujos-funcionales-mvp.md`: comportamiento funcional, reglas de negocio, estados y casos borde.
- `auth/google-mvp.md`: login Google, sesion propia Turnero, cookie segura y `user_sessions`.
- `../referencias/guia-historias-tecnicas.md`: referencia para escribir subtareas tecnicas, criterios de aceptacion y Definition of Done.

La idea es que cada item de este plan pueda convertirse en una subtarea Jira y en un PR con proposito claro.

## Punto de partida historico del plan

Esta seccion registra el estado que motivo este plan de migracion. No describe el estado actual del repositorio: para eso consultar `tracking-implementacion-mvp.md`.

Al crear el plan, el backend era una base funcional single-business.

Existe:

- Spring Boot 3.5, Java 21, Gradle, JPA.
- H2 configurado en `src/main/resources/application.properties`.
- Controllers, services, repositories, entities, DTOs, mappers MapStruct y tests.
- CRUD base de `Customer`, `ServiceOffering`, `StaffMember` y `Appointment`.
- Validaciones con Bean Validation.
- `GlobalExceptionHandler`.
- Logging basico con `@Slf4j` en services y handler global.
- Validacion de referencias en appointments.
- Validacion de solapamiento por `staffMemberId`.

No existe todavia:

- PostgreSQL como DB objetivo.
- Docker Compose para DB local.
- `.env` local no versionado ni `.env.example`.
- Perfiles Spring `dev`, `test`, `desa`, `prod`.
- Flyway.
- `Business`, `User`, `UserSession`.
- Scoping por `business_id`.
- `BookingSettings`, `BusinessHours`, `StaffWorkingHours`.
- `AvailabilityException`.
- `AppointmentPublicToken`.
- Google Auth.
- Endpoints publicos.
- Request id, Actuator, health checks customizados, logs JSON o configuracion avanzada por ambiente.

Brechas relevantes:

- `Appointment` usa `dateTime` y `durationMinutes`; el MVP requiere `starts_at`, `ends_at`, `duration_minutes` y `price_cents`.
- `ServiceOffering` usa precio decimal; el MVP requiere `price_cents`.
- Las entidades actuales no tienen relaciones JPA ni scope por negocio segun el schema MVP.
- Los endpoints actuales no estan versionados bajo `/api/v1`.

## Decisiones cerradas para esta migracion

- PostgreSQL es la DB objetivo.
- Flyway es la herramienta recomendada para migraciones.
- La migracion completa de schema puede hacerse en un solo PR grande porque el repo esta en desarrollo y no hay datos productivos que preservar.
- Ese PR grande puede incluir DB, migracion completa, entidades, enums y repositories base.
- Los PRs de features deben ser chicos: objetivo unico, aproximadamente 8 a 15 archivos, y no mas de 2 endpoints nuevos o migrados.
- Los PRs de arquitectura pueden exceder ese limite cuando el motivo este explicitado.
- Primero se usa un contexto dev temporal de negocio para probar endpoints admin desde frontend; Google Auth entra despues y reemplaza ese contexto.
- La integracion real con frontend comienza por auth/BFF y configuracion sobre contratos estables; agenda entra cuando se cierre el contrato de appointments.
- Las rutas se migran a `/api/v1` por recurso a medida que se toca cada feature.
- No se mantiene doble ruta `/api` y `/api/v1`, salvo que una subtarea lo justifique explicitamente.
- JSON `snake_case` se implementa con DTOs v1 anotados por recurso, no con un cambio global temprano de Jackson.
- Tests: H2 para tests rapidos donde alcance, PostgreSQL/Testcontainers selectivo para Flyway, constraints y concurrencia.
- `availability_exceptions` se crea en schema y se lee desde availability, pero no tiene CRUD admin en el MVP inicial.
- El seed dev debe alinearse con `../turnero-frontend/docs/referencias/datos-demo.md`.

## Reglas para cada subtarea / PR

Cada PR debe tener:

- Titulo con formato de subtarea Jira.
- Objetivo de una frase.
- Endpoints afectados, maximo 2 si es feature.
- Cambios DB/schema, aunque sea "sin cambios de schema".
- Cambios backend: entidades, repositories, services, controllers, DTOs y mappers afectados.
- Tests esperados.
- Criterios de aceptacion.
- Riesgos o validaciones.
- Fuera de alcance.

En este plan, cuando una subtarea feature dice "Cambios esperados", se entiende como cambios backend esperados. Si no se menciona DB/schema en esa subtarea, el default es: sin cambios de schema, porque el schema completo se crea en PR A1.

Reglas de tamano:

- Feature PR: idealmente 8 a 15 archivos.
- Si un PR feature supera 15 archivos, dividirlo.
- Si un PR feature necesita mas de 2 endpoints, dividirlo.
- No mezclar infraestructura, schema, auth y comportamiento de negocio en el mismo PR, salvo PRs de arquitectura marcados.
- No hacer refactors generales no relacionados con la subtarea.
- Mantener `./gradlew test` verde al cierre de cada PR.

Reglas de contrato:

- Endpoints nuevos o migrados salen bajo `/api/v1`.
- Requests, responses y query params usan `snake_case`.
- DTOs Java mantienen convencion local, por ejemplo `BusinessSummaryDTO`, `BusinessResponseDTO`, `BusinessUpdateRequestDTO`.
- El cliente admin nunca envia `business_id`.
- Todo endpoint admin usa el negocio del contexto actual.
- Recursos fuera del `business_id` actual deben responder como no encontrados cuando convenga no revelar existencia.
- No duplicar schema ni contratos completos en este documento; referenciar `schema-db-mvp.md` y `api-contracts-mvp.md`.

## Infraestructura y ambientes

### PostgreSQL local

Agregar Docker Compose para PostgreSQL local.

Variables esperadas:

```text
POSTGRES_DB=turnero
POSTGRES_USER=turnero
POSTGRES_PASSWORD=turnero_local
POSTGRES_PORT=5432
```

Requisitos:

- Imagen PostgreSQL alineada con futuro AWS RDS.
- Volumen local para persistencia.
- Healthcheck del contenedor.
- Sin dumps ni datos reales versionados.

### Makefile local

Agregar un `Makefile` de conveniencia adaptado a Turnero API, Java, Gradle, Spring Boot, Docker Compose, PostgreSQL y Flyway.

Objetivo:

- Evitar que cada dev recuerde comandos largos.
- Unificar la forma local de levantar DB, correr la app y correr tests.
- Facilitar los primeros PRs a una desarrolladora junior inicial.

Targets minimos sugeridos:

- `make db-up`: levantar PostgreSQL local con Docker Compose.
- `make db-down`: bajar PostgreSQL local.
- `make db-logs`: ver logs de PostgreSQL local.
- `make run`: levantar la API con perfil `dev`.
- `make test`: correr `./gradlew test`.
- `make clean`: correr `./gradlew clean`.

Si se agrega target de migraciones, debe respetar la configuracion elegida de Flyway. No debe duplicar SQL ni reemplazar las migraciones versionadas.

### `.env` y `.env.example`

Agregar:

- `.env.example` versionado con nombres de variables y valores dummy seguros.
- `.env` local no versionado.
- Regla en `.gitignore` si no existe.

Variables minimas:

```text
SPRING_PROFILES_ACTIVE=dev
DB_HOST=localhost
DB_PORT=5432
DB_NAME=turnero
DB_USERNAME=turnero
DB_PASSWORD=turnero_local
GOOGLE_CLIENT_ID=
AUTH_SESSION_COOKIE_NAME=turnero_session
AUTH_SESSION_TTL_DAYS=7
AUTH_SESSION_SECURE=false
AUTH_SESSION_SAME_SITE=Lax
```

El MVP usa ID token y no requiere `GOOGLE_CLIENT_SECRET`. En produccion HTTPS usar `AUTH_SESSION_COOKIE_NAME=__Host-turnero_session` y `AUTH_SESSION_SECURE=true`. El admin MVP accede mediante BFF same-origin de Next, por lo que no requiere una variable CORS backend.

### Perfiles Spring

`dev`:

- Desarrollo local.
- PostgreSQL en Docker.
- Flyway activo.
- Seed dev Barber Studio disponible.
- Cookie `Secure=false` solo si no hay HTTPS local.
- CORS limitado al frontend local.

`test`:

- Tests automatizados.
- H2 para tests rapidos si no dependen de comportamiento PostgreSQL.
- PostgreSQL/Testcontainers para Flyway, constraints y concurrencia.

`desa`:

- Ambiente compartido de desarrollo.
- Secretos fuera del repo.
- CORS por dominio del frontend desa.
- Cookies `Secure=true` si usa HTTPS.

`prod`:

- Queda para `plan-deploy-aws-mvp.md`.
- Debe asumir RDS PostgreSQL, secretos gestionados, backups, migraciones controladas y observabilidad.

## Secuencia de PRs

### Estado de implementacion

El estado real de avance contra este plan se mantiene en `tracking-implementacion-mvp.md`.

Este documento define el orden, alcance y criterios esperados de cada PR, pero no debe usarse como fuente de tracking.

### PR A1 - Preparar PostgreSQL, Flyway y schema MVP completo

Objetivo:

Dejar la base tecnica de persistencia lista para implementar features contra el modelo MVP.

Tipo:

Arquitectura. Puede superar 15 archivos.

Endpoints afectados:

- Ninguno.

Cambios esperados:

- Agregar dependencia PostgreSQL.
- Agregar Flyway.
- Agregar Docker Compose.
- Agregar `Makefile` local de conveniencia para DB, app y tests.
- Agregar `.env.example` y proteger `.env`.
- Crear perfiles `dev`, `test`, `desa`, `prod`.
- Crear migracion inicial completa basada en `schema-db-mvp.md`.
- Crear enums backend para estados/controlados del MVP.
- Crear entidades JPA principales del schema MVP.
- Crear repositories base.
- Agregar seed dev Barber Studio alineado a `../turnero-frontend/docs/referencias/datos-demo.md`.

Tests esperados:

- `./gradlew test`.
- Test de contexto Spring con perfil `test`.
- Test Flyway/Testcontainers que valide que la migracion inicial corre sobre PostgreSQL.

Criterios de aceptacion:

- La app levanta en `dev` contra PostgreSQL local.
- `make db-up`, `make db-down`, `make run` y `make test` funcionan localmente.
- La suite sigue pasando.
- No hay secretos versionados.
- El schema creado representa `schema-db-mvp.md`.

Riesgos/validaciones:

- Mantener este PR enfocado en arquitectura DB, sin endpoints nuevos.
- No intentar resolver reglas de negocio complejas en services todavia.
- El Makefile debe ser una capa fina sobre Docker Compose y Gradle, no una segunda fuente de configuracion.

Fuera de alcance:

- Auth Google.
- Endpoints publicos.
- CRUD admin nuevo.

### PR A2 - Agregar contexto dev de negocio

Objetivo:

Permitir que endpoints admin funcionen scopeados a un negocio antes de implementar auth real.

Endpoints afectados:

- Ninguno nuevo.

Cambios esperados:

- Crear un `CurrentBusinessContext` o helper equivalente para resolver el business seed en perfil `dev/test`.
- Preparar services para recibir `business_id` desde contexto, no desde request.
- Documentar que este contexto es temporal hasta PR de auth.

Tests esperados:

- Test unitario del resolver/contexto.
- Test de service/repository que confirme que un recurso se consulta por `business_id`.

Criterios de aceptacion:

- Ningun request admin necesita enviar `business_id`.
- El contexto dev permite probar endpoints con el business seed.
- El codigo queda facil de reemplazar por usuario autenticado.

Riesgos/validaciones:

- No hardcodear el business en services de dominio.
- Aislar el contexto temporal para poder removerlo.

Fuera de alcance:

- Google Auth.
- Cookies.
- Roles.

### PR A3 - Alinear errores al contrato MVP

Objetivo:

Unificar respuestas de error antes de migrar endpoints a `/api/v1`.

Endpoints afectados:

- Todos indirectamente por `GlobalExceptionHandler`.

Cambios esperados:

- Ajustar `ErrorResponse` al formato de `api-contracts-mvp.md`.
- Soportar `code`, `message`, `details`, `path` y `timestamp`.
- Mantener mapeos para validation, not found, conflict, unauthenticated, forbidden y generic.
- Evitar exponer mensajes internos en errores inesperados.

Tests esperados:

- Tests de handler para validation, not found, conflict y generic.
- Test de controller real que valide estructura JSON de error.

Criterios de aceptacion:

- Los errores tienen formato estable.
- Los tests validan body, no solo HTTP status.
- Errores inesperados no exponen stacktrace ni detalles internos.

Riesgos/validaciones:

- No mezclar con migracion de rutas ni DTOs de recursos.

Fuera de alcance:

- Cambios de endpoints.
- Auth.

### PR A4 - Agregar observabilidad minima

Objetivo:

Dejar logging y health checks basicos antes de exponer mas superficie HTTP.

Endpoints afectados:

- Health endpoints de Actuator.

Cambios esperados:

- Agregar Actuator.
- Agregar request id por request.
- Incluir request id en logs.
- Definir politica de no loguear cookies, session tokens, Google ID tokens ni public cancellation tokens.
- Configurar exposicion segura de health por perfil.

Tests esperados:

- Test de filtro/interceptor de request id.
- Test simple de health si aplica.
- `./gradlew test`.

Criterios de aceptacion:

- Cada request tiene request id trazable.
- Health basico esta disponible.
- No se loguean tokens ni cookies.

Riesgos/validaciones:

- No exponer Actuator completo por accidente.

Fuera de alcance:

- Logs JSON avanzados.
- Metricas custom.

## PRs para integracion inicial de agenda admin

### PR 1 - Service offerings: listar y crear

Objetivo:

Permitir al frontend listar y crear servicios para poder armar datos de agenda y formularios.

Endpoints afectados:

- `GET /api/v1/service-offerings`
- `POST /api/v1/service-offerings`

Cambios esperados:

- Crear controller v1 del recurso.
- Crear DTOs v1 con JSON `snake_case`.
- Crear service methods scopeados por `business_id`.
- Usar `price_cents`, `duration_minutes` y `status`.
- Soportar filtros basicos del contrato si entran en el limite del PR.

Tests esperados:

- Controller test de list.
- Controller test de create.
- Service test de scoping por `business_id`.
- Validaciones de `duration_minutes > 0` y `price_cents >= 0`.

Criterios de aceptacion:

- Crear servicio no acepta `business_id` desde request.
- La respuesta usa `snake_case`.
- Los servicios se guardan con `status = ACTIVE` por default si el contrato lo permite.

Riesgos/validaciones:

- No migrar detalle, update ni delete en este PR.

Fuera de alcance:

- Asociacion staff-service.
- Booking publico.

### PR 2 - Staff members: listar y crear

Objetivo:

Permitir al frontend listar y crear profesionales para filtros de agenda.

Endpoints afectados:

- `GET /api/v1/staff-members`
- `POST /api/v1/staff-members`

Cambios esperados:

- Crear controller v1 del recurso.
- Crear DTOs v1 con JSON `snake_case`.
- Crear service methods scopeados.
- Al crear staff, crear `staff_working_hours` copiando `business_hours`.
- Soportar `status = ACTIVE` por default.

Tests esperados:

- Controller test de list.
- Controller test de create.
- Service test que valida copia de business hours.
- Test de `user_id` de otro negocio si se informa.

Criterios de aceptacion:

- Crear profesional no acepta `business_id`.
- El profesional creado queda activo.
- Quedan horarios de trabajo iniciales creados para ese staff.

Riesgos/validaciones:

- Si no hay `business_hours`, devolver error funcional claro o asegurar seed/default desde PR A1.

Fuera de alcance:

- Working hours endpoint.
- Baja logica de staff.

### PR 3 - Staff-service offerings: listar y reemplazar asociaciones

Objetivo:

Definir que servicios ofrece cada profesional.

Endpoints afectados:

- `GET /api/v1/staff-members/{staff_member_id}/service-offerings`
- `PUT /api/v1/staff-members/{staff_member_id}/service-offerings`

Cambios esperados:

- Crear controller v1 para asociaciones.
- Crear DTOs v1.
- Reemplazar asociaciones del profesional por la lista enviada.
- Validar que staff y servicios pertenezcan al business actual.
- Rechazar servicios `INACTIVE`.

Tests esperados:

- Listar servicios asociados.
- Reemplazar asociaciones.
- Rechazar staff de otro negocio.
- Rechazar servicio de otro negocio.
- Rechazar servicio inactivo.

Criterios de aceptacion:

- No se crean asociaciones duplicadas.
- Remover una asociacion no modifica appointments existentes.

Riesgos/validaciones:

- Evitar N+1 al devolver asociaciones si se vuelve visible.

Fuera de alcance:

- Detalle de servicio con staff embebido.
- Booking publico.

### PR 4 - Customers: listar y crear

Objetivo:

Permitir al frontend listar clientes y crear cliente rapido para turnos.

Endpoints afectados:

- `GET /api/v1/customers`
- `POST /api/v1/customers`

Cambios esperados:

- Crear controller v1 del recurso.
- Crear DTOs v1.
- Crear service methods scopeados.
- Soportar busqueda simple `q` y paginacion si entra sin exceder el PR.

Tests esperados:

- Controller test de list.
- Controller test de create.
- Validaciones de name/email/phone.
- Scoping por `business_id`.

Criterios de aceptacion:

- El cliente se crea dentro del business actual.
- La respuesta no expone datos internos fuera del contrato.

Riesgos/validaciones:

- No sobredefinir unicidad global si debe ser por negocio.

Fuera de alcance:

- Detalle, update y delete de customer.
- Historial de cliente.

### PR 5 - Appointments admin: agenda diaria y crear turno

Objetivo:

Permitir que la agenda admin muestre turnos del dia y cree turnos manuales.

Endpoints afectados:

- `GET /api/v1/appointments`
- `POST /api/v1/appointments`

Cambios esperados:

- Crear controller v1 de appointments.
- Crear DTOs v1.
- `GET` soporta `date` o `from/to` segun contrato.
- `POST` acepta cliente existente o cliente rapido.
- Backend calcula `ends_at`, `duration_minutes` y `price_cents`.
- Backend valida customer, service, staff y staff-service.
- Backend valida no solapamiento para estados bloqueantes.
- `source = ADMIN`.

Tests esperados:

- Listar agenda por dia en timezone del business.
- Crear turno con cliente existente.
- Crear turno con cliente rapido.
- Calcular snapshots.
- Rechazar staff que no ofrece el servicio.
- Rechazar solapamiento.
- Rechazar recursos de otro negocio.

Criterios de aceptacion:

- El frontend puede renderizar agenda diaria con datos reales.
- No se persiste appointment sin `staff_member_id`.
- `price_cents` y `duration_minutes` quedan guardados como snapshot.

Riesgos/validaciones:

- Este PR toca una zona central; si supera 15 archivos, priorizar create/list y dejar filtros avanzados para otro PR.

Fuera de alcance:

- Detalle/update de appointment.
- Acciones confirm/cancel.
- Availability endpoint.

### PR 6 - Appointments admin: detalle y edicion

Objetivo:

Permitir abrir y editar un turno desde agenda.

Endpoints afectados:

- `GET /api/v1/appointments/{appointment_id}`
- `PATCH /api/v1/appointments/{appointment_id}`

Cambios esperados:

- Agregar endpoints al controller v1 de appointments.
- Reusar DTOs v1 o crear update DTO especifico.
- Validar scoping del appointment.
- Si cambia servicio, recalcular `ends_at`, `duration_minutes` y `price_cents`.
- Validar solapamiento excluyendo el mismo appointment.

Tests esperados:

- Obtener detalle.
- Editar horario.
- Editar servicio y recalcular snapshots.
- Rechazar update con solapamiento.
- Rechazar appointment de otro negocio.

Criterios de aceptacion:

- La respuesta actualizada sigue contrato.
- `created_at` no cambia en update.
- `updated_at` se actualiza.

Riesgos/validaciones:

- No mezclar transiciones de estado especiales en este PR.

Fuera de alcance:

- Confirmar y cancelar; se cubren en PR 7.
- Completar y marcar no-show; se cubren en PR 7b.

### PR 7 - Appointments admin: confirmar y cancelar

Objetivo:

Permitir acciones principales sobre turnos pendientes desde agenda.

Endpoints afectados:

- `POST /api/v1/appointments/{appointment_id}/confirm`
- `POST /api/v1/appointments/{appointment_id}/cancel`

Cambios esperados:

- Centralizar transiciones de estado.
- `PENDING -> CONFIRMED`.
- `PENDING|CONFIRMED -> CANCELLED`.
- Guardar `cancellation_reason` si se informa.

Tests esperados:

- Confirmar pending.
- Rechazar confirmar appointment no pending.
- Cancelar pending/confirmed.
- Rechazar cancelar appointment ya cancelado si se decide estado invalido.
- Scoping por business.

Criterios de aceptacion:

- Transiciones invalidas responden `409 INVALID_STATE_TRANSITION`.
- Cancelar no borra el appointment.

Riesgos/validaciones:

- Mantener reglas en service, no en controller.

Fuera de alcance:

- Completar y marcar no-show; se cubren en PR 7b.

### PR 7b - Appointments admin: completar y marcar no-show

Objetivo:

Permitir cerrar el ciclo operativo de un turno desde agenda.

Endpoints afectados:

- `POST /api/v1/appointments/{appointment_id}/complete`
- `POST /api/v1/appointments/{appointment_id}/no-show`

Cambios esperados:

- Reusar la logica centralizada de transiciones de estado.
- Permitir marcar como `COMPLETED` solo appointments en estado operativo valido.
- Permitir marcar como `NO_SHOW` solo appointments en estado operativo valido.
- No borrar appointments ni modificar customer, staff, service, precio o duracion.

Tests esperados:

- Completar appointment confirmado.
- Marcar appointment confirmado como no-show.
- Rechazar completar appointment cancelado.
- Rechazar no-show sobre appointment cancelado.
- Scoping por business.

Criterios de aceptacion:

- Transiciones invalidas responden `409 INVALID_STATE_TRANSITION`.
- `COMPLETED` y `NO_SHOW` no bloquean disponibilidad futura.

Riesgos/validaciones:

- Mantener reglas en service, no en controller.
- No duplicar logica de transiciones respecto de PR 7.

Fuera de alcance:

- Reabrir appointments completados o no-show.

### PR 8 - Availability admin: slots para crear/editar turno

Objetivo:

Permitir que el frontend consulte horarios disponibles para crear o editar turnos.

Endpoints afectados:

- `GET /api/v1/availability/slots`

Cambios esperados:

- Crear `AvailabilityService`.
- Usar `business_hours`.
- Usar `staff_working_hours`.
- Leer `availability_exceptions`.
- Excluir appointments `PENDING` y `CONFIRMED`.
- Respetar duracion del servicio.
- Soportar `exclude_appointment_id`.
- Limitar rango maximo a 31 dias.

Tests esperados:

- Slots dentro de horario del negocio y staff.
- Sin slots cuando business/staff esta cerrado.
- Appointment pending/confirmed bloquea slot.
- Appointment cancelled/completed/no-show no bloquea.
- `exclude_appointment_id` no se bloquea a si mismo.
- Rango mayor al maximo se rechaza.

Criterios de aceptacion:

- El frontend puede mostrar slots disponibles para el drawer/form de turno.
- La disponibilidad no reemplaza la validacion transaccional al crear appointment.

Riesgos/validaciones:

- Evitar N+1 al consultar appointments/staff.
- Mantener algoritmo simple y testeado.

Fuera de alcance:

- Public availability con `staff_member_id=any`.
- CRUD de availability exceptions.

## PRs de gestion admin restante

### PR 9 - Service offerings: detalle y edicion

Objetivo:

Permitir editar servicios existentes.

Endpoints afectados:

- `GET /api/v1/service-offerings/{service_offering_id}`
- `PATCH /api/v1/service-offerings/{service_offering_id}`

Cambios esperados:

- Detalle con datos del servicio.
- Update de name, category, duration, price y status segun contrato.
- Cambios de precio/duracion no modifican appointments existentes.

Tests esperados:

- Obtener detalle.
- Editar precio/duracion.
- Confirmar que appointment historico conserva snapshot.
- Rechazar recurso fuera de scope.

Criterios de aceptacion:

- Update responde el servicio actualizado.
- El contrato usa `snake_case`.

Fuera de alcance:

- Delete/baja logica.

### PR 10 - Service offerings: baja logica

Objetivo:

Permitir desactivar servicios sin borrar historial.

Endpoints afectados:

- `DELETE /api/v1/service-offerings/{service_offering_id}`

Cambios esperados:

- Setear `status = INACTIVE`.
- Mantener appointments historicos.

Tests esperados:

- Baja exitosa.
- Recurso inexistente o fuera de scope.
- Servicio inactivo no se asocia en PR 3.

Criterios de aceptacion:

- El registro no se borra fisicamente.

Fuera de alcance:

- Reactivacion especial.

### PR 11 - Staff members: detalle y edicion

Objetivo:

Permitir editar profesionales existentes.

Endpoints afectados:

- `GET /api/v1/staff-members/{staff_member_id}`
- `PATCH /api/v1/staff-members/{staff_member_id}`

Cambios esperados:

- Detalle de staff.
- Update de name, role_label, specialty, avatar_url y status segun contrato.

Tests esperados:

- Obtener detalle.
- Editar campos.
- Rechazar recurso fuera de scope.

Criterios de aceptacion:

- No se modifica historial de appointments.

Fuera de alcance:

- Baja logica.
- Working hours.

### PR 12 - Staff members: baja logica

Objetivo:

Permitir desactivar profesionales sin borrar historial.

Endpoints afectados:

- `DELETE /api/v1/staff-members/{staff_member_id}`

Cambios esperados:

- Setear `status = INACTIVE`.
- Bloquear baja si tiene appointments futuros `PENDING` o `CONFIRMED`.

Tests esperados:

- Baja exitosa sin turnos futuros activos.
- Conflicto con turnos futuros activos.
- Recurso fuera de scope.

Criterios de aceptacion:

- No se borra historial.
- El conflicto responde `409`.

Fuera de alcance:

- Reasignacion automatica de turnos.

### PR 13 - Staff working hours: listar y reemplazar semana

Objetivo:

Permitir editar horarios de trabajo del profesional.

Endpoints afectados:

- `GET /api/v1/staff-members/{staff_member_id}/working-hours`
- `PUT /api/v1/staff-members/{staff_member_id}/working-hours`

Cambios esperados:

- Listar semana completa.
- Reemplazar semana completa.
- Exigir 7 dias para evitar ambiguedad.
- Validar `starts_at < ends_at` cuando `is_available = true`.

Tests esperados:

- Listar horarios.
- Reemplazar semana.
- Rechazar semana incompleta.
- Rechazar rango invalido.
- Scoping por staff/business.

Criterios de aceptacion:

- Availability usa los horarios actualizados.

Fuera de alcance:

- Excepciones puntuales.

### PR 14 - Customers: detalle y edicion

Objetivo:

Permitir ver y editar datos de cliente.

Endpoints afectados:

- `GET /api/v1/customers/{customer_id}`
- `PATCH /api/v1/customers/{customer_id}`

Cambios esperados:

- Detalle con datos operativos permitidos.
- Update de name, email, phone_number, internal_notes y status.

Tests esperados:

- Obtener detalle.
- Editar campos.
- Rechazar recurso fuera de scope.

Criterios de aceptacion:

- No se exponen datos fuera del contrato.

Fuera de alcance:

- Historial detallado.
- Baja logica.

### PR 15 - Customers: baja logica

Objetivo:

Permitir desactivar clientes sin borrar historial.

Endpoints afectados:

- `DELETE /api/v1/customers/{customer_id}`

Cambios esperados:

- Setear `status = INACTIVE`.
- Mantener appointments historicos.

Tests esperados:

- Baja exitosa.
- Recurso inexistente o fuera de scope.

Criterios de aceptacion:

- El registro no se borra fisicamente.

Fuera de alcance:

- Portal cliente.

### PR 16 - Business: ver y editar datos base

Objetivo:

Permitir que Configuracion admin lea y edite datos base del negocio.

Endpoints afectados:

- `GET /api/v1/business`
- `PATCH /api/v1/business`

Cambios esperados:

- Devolver business del contexto actual.
- Editar name, industry, email, phone, address y timezone.
- No permitir editar slug, status ni onboarding_status desde este endpoint.
- Recalcular onboarding si aplica.

Tests esperados:

- Obtener business.
- Editar datos permitidos.
- Ignorar o rechazar campos no editables segun DTO.

Criterios de aceptacion:

- El frontend puede alimentar pantalla de Configuracion base.

Fuera de alcance:

- Provisioning publico.

### PR 17 - Booking settings: ver y editar reglas

Objetivo:

Permitir configurar reglas de reserva.

Endpoints afectados:

- `GET /api/v1/booking-settings`
- `PATCH /api/v1/booking-settings`

Cambios esperados:

- Leer settings del business actual.
- Editar public_booking_enabled, booking_window_days, min_notice_hours, cancellation_notice_hours, slot_interval_minutes y manual_confirmation_enabled.
- Rechazar `requires_customer_login = true`.
- Mantener WhatsApp reminders fuera del MVP.

Tests esperados:

- Obtener settings.
- Editar settings validos.
- Rechazar requires_customer_login true.
- Validar slot interval permitido.

Criterios de aceptacion:

- Availability/public booking consumen settings actualizados.

Fuera de alcance:

- Recordatorios WhatsApp.

### PR 18 - Business hours: ver y reemplazar semana

Objetivo:

Permitir configurar horarios generales del negocio.

Endpoints afectados:

- `GET /api/v1/business-hours`
- `PUT /api/v1/business-hours`

Cambios esperados:

- Listar semana completa.
- Reemplazar semana completa.
- Exigir 7 dias.
- Validar `opens_at < closes_at` cuando `is_closed = false`.

Tests esperados:

- Obtener horarios.
- Reemplazar semana.
- Rechazar semana incompleta.
- Rechazar rango invalido.

Criterios de aceptacion:

- Availability usa business hours actualizados.

Fuera de alcance:

- Excepciones puntuales.

## PRs de auth y proteccion admin

Estado: PR 19 y PR 20 ya fueron mergeados en `develop` mediante PRs #61 y #62. Este documento conserva el alcance planificado; las diferencias pendientes respecto del contrato canónico y el hardening viven en `tracking-implementacion-mvp.md` y `deuda-tecnica-backend.md`.

### PR 19 - Auth Google: login y sesion actual

Objetivo:

Permitir login owner con Google y crear sesion propia Turnero.

Endpoints afectados:

- `POST /api/v1/auth/google`
- `GET /api/v1/auth/me`

Cambios esperados:

- Implementar `AuthController`.
- Implementar `GoogleIdentityService`.
- Implementar `SessionService`.
- Validar `email_verified = true`.
- Buscar user por `auth_provider = GOOGLE` y `auth_subject`.
- Crear `user_sessions` con hash del token.
- Setear cookie HTTP-only.

Tests esperados:

- Login valido crea sesion hasheada.
- `email_verified=false` responde 401.
- Usuario sin business responde 403.
- `/auth/me` devuelve user y business.
- Sesion vencida o revocada responde 401.

Criterios de aceptacion:

- El frontend no necesita leer token desde JavaScript.
- No se guarda token plano en DB.

Riesgos/validaciones:

- No loguear ID token ni cookie.
- En local, documentar excepcion `Secure=false`.

Fuera de alcance:

- Logout.
- Proteger todos los endpoints admin.

### PR 20 - Auth admin: logout y proteccion de endpoints admin

Objetivo:

Reemplazar contexto dev por usuario autenticado en endpoints admin.

Endpoints afectados:

- `POST /api/v1/auth/logout`
- Endpoints admin existentes quedan protegidos.

Cambios esperados:

- Implementar logout revocando `user_sessions.revoked_at`.
- Expirar cookie.
- Agregar filtro/interceptor de auth para endpoints admin.
- Reemplazar `CurrentBusinessContext` dev por business del user autenticado.
- Mantener bypass solo en tests si es necesario y esta explicitado.

Tests esperados:

- Logout revoca sesion.
- Endpoint admin sin cookie responde 401.
- Endpoint admin con sesion valida funciona.
- Scoping sale de `users.business_id`.

Criterios de aceptacion:

- Ya no se depende del contexto dev para uso normal.
- La autorizacion MVP acepta rol `OWNER`.

Riesgos/validaciones:

- CORS y CSRF deben quedar definidos antes de ambiente compartido.

Fuera de alcance:

- Roles completos no OWNER.

## PRs de booking publico

### PR 21 - Public booking: perfil y servicios

Objetivo:

Permitir que la pantalla publica inicial cargue negocio y servicios reservables.

Endpoints afectados:

- `GET /api/v1/public/businesses/{business_slug}/booking-profile`
- `GET /api/v1/public/businesses/{business_slug}/services`

Cambios esperados:

- Resolver business por slug.
- Validar business activo y public booking enabled.
- Devolver datos publicos minimos.
- Listar solo servicios `ACTIVE` con al menos un staff `ACTIVE` asociado.
- No exponer notas internas.

Tests esperados:

- Perfil publico exitoso.
- Slug inexistente responde 404.
- Booking deshabilitado responde 403.
- Servicios sin staff activo no aparecen.

Criterios de aceptacion:

- Booking frontend puede cargar marca, settings publicos y servicios.

Riesgos/validaciones:

- No requerir cookie admin.

Fuera de alcance:

- Availability publica.
- Crear reserva.

### PR 22 - Public booking: availability

Objetivo:

Permitir consultar slots publicos para un servicio.

Endpoints afectados:

- `GET /api/v1/public/businesses/{business_slug}/availability`

Cambios esperados:

- Reusar `AvailabilityService`.
- Respetar booking_window_days y min_notice_hours.
- Soportar `staff_member_id` numerico.
- Soportar `staff_member_id=any`.
- Devolver `available_staff_member_ids` cuando aplica.

Tests esperados:

- Slots para staff especifico.
- Slots para `any`.
- Rechazar staff que no ofrece servicio.
- Respetar min_notice_hours.
- Respetar booking_window_days.

Criterios de aceptacion:

- El frontend puede mostrar horarios publicos disponibles.

Riesgos/validaciones:

- Availability es informativa; crear reserva debe revalidar.

Fuera de alcance:

- Crear appointment publico.

### PR 23 - Public booking: crear reserva

Objetivo:

Permitir crear una reserva publica sin login.

Endpoints afectados:

- `POST /api/v1/public/businesses/{business_slug}/appointments`

Cambios esperados:

- Buscar o crear customer dentro del business.
- Validar servicio activo y staff asignable.
- Resolver `staff_member_id=any` a un staff real.
- Revalidar disponibilidad dentro de transaccion.
- Crear appointment con `source = PUBLIC_BOOKING`.
- Setear status segun `manual_confirmation_enabled`.
- Crear `appointment_public_tokens` tipo `CANCEL`, guardar hash y devolver token plano solo en response.

Tests esperados:

- Crear con staff especifico.
- Crear con `any`.
- Manual confirmation true crea `PENDING`.
- Manual confirmation false crea `CONFIRMED`.
- Conflicto de slot responde 409.
- Token se guarda hasheado.

Criterios de aceptacion:

- No se acepta price, duration, ends_at, status ni source desde request publico.
- No se guarda appointment sin staff real.

Riesgos/validaciones:

- Prevenir doble reserva con transaccion/locking o constraint adecuado.
- No loguear token publico.

Fuera de alcance:

- Cancelacion publica.

### PR 24 - Public cancellation: validar y cancelar con token

Objetivo:

Permitir cancelar una reserva sin login usando token seguro.

Endpoints afectados:

- `GET /api/v1/public/cancellations/{token}`
- `POST /api/v1/public/cancellations/{token}`

Cambios esperados:

- Hashear token recibido.
- Validar type `CANCEL`, `used_at` null y `expires_at` vigente.
- Validar cancellation_notice_hours.
- `GET` devuelve resumen publico y `can_cancel`.
- `POST` cancela appointment, guarda razon y marca token usado.

Tests esperados:

- Token invalido.
- Token vencido.
- Token usado.
- Appointment ya cancelado.
- Cancelacion fuera de plazo.
- Cancelacion exitosa.

Criterios de aceptacion:

- No se usa `appointment_id` como autorizacion publica.
- Token usado no vuelve a operar.

Riesgos/validaciones:

- No loguear token.

Fuera de alcance:

- Reprogramacion publica.

## PR final de hardening MVP

### PR 25 - Hardening operativo MVP

Objetivo:

Cerrar seguridad, concurrencia y operacion minima antes de declarar backend MVP listo.

Tipo:

Arquitectura/hardening. Puede superar 15 archivos si se justifica.

Endpoints afectados:

- Public booking.
- Public cancellation.
- Auth/session.
- Health.

Cambios esperados:

- Rate limiting en endpoints publicos.
- CORS final por ambiente.
- Decision CSRF documentada e implementada si aplica.
- Cleanup de sesiones vencidas.
- Cleanup o invalidacion de tokens publicos vencidos.
- Tests de concurrencia para doble reserva.
- Revision de indices principales.
- Documentar comandos de run/test y verificacion manual.

Tests esperados:

- Test de rate limiting si la libreria lo permite.
- Test de cleanup testeable.
- Test de doble reserva con PostgreSQL/Testcontainers.
- `./gradlew test`.

Criterios de aceptacion:

- Endpoints publicos tienen proteccion minima contra abuso.
- No quedan tokens/cookies en logs.
- La doble reserva esta cubierta por test.

Fuera de alcance:

- Backups y rollback productivo; eso vive en `plan-deploy-aws-mvp.md`.

## Checklist robustez/escalabilidad mapeado a PRs

| Tema | PRs |
| --- | --- |
| PostgreSQL local | A1 |
| Flyway | A1 |
| Schema MVP completo | A1 |
| Seed dev Barber Studio | A1 |
| `.env` y perfiles | A1 |
| Scoping por `business_id` | A2, PRs 1-18, PR 20 |
| Errores estables | A3 |
| Logs utiles y request id | A4 |
| Health checks | A4 |
| No loguear tokens/cookies | A4, PR 19, PR 23, PR 24, PR 25 |
| Agenda admin testeable con frontend | PRs 1-8 y PR 7b |
| Ciclo de vida admin de appointments | PR 7, PR 7b |
| Availability admin | PR 8 |
| Availability exceptions lectura interna | A1, PR 8 |
| Auth/session | PRs 19-20 |
| BFF same-origin para admin frontend | TURN-69 frontend |
| CORS para consumidores directos | Fuera del admin MVP; PR 25 si aparece el requisito |
| CSRF segun arquitectura final | PR 25 |
| Public booking | PRs 21-23 |
| Public cancellation token | PR 24 |
| Rate limiting publico | PR 25 |
| Doble reserva/concurrencia | PR 5, PR 23, PR 25 |
| Cleanup sesiones/tokens | PR 25 |
| Backups y rollback | `plan-deploy-aws-mvp.md` |

## Fuera del MVP

- Registro publico self-service de negocios.
- Login email/password propio.
- Portal cliente con login.
- Roles operativos completos mas alla de `OWNER`.
- Notificaciones reales y tabla `notifications` implementada en producto.
- Recordatorios WhatsApp.
- CRUD admin de `availability_exceptions` en el MVP inicial.
- Reprogramacion publica.
- Pagos.
- Multi-business por usuario.
- Multi-sucursal.
- Plan AWS final, salvo decisiones necesarias para no bloquear configuracion local.

## Definition of Ready inicial de la migracion

Esta lista se uso para iniciar la migracion. Para evaluar una subtarea actual, usar sus criterios en este plan, Jira y el tracking.

Antes de iniciar la migracion backend del MVP debia estar cumplido:

- Este plan revisado y aceptado.
- PostgreSQL confirmado como DB objetivo.
- Flyway aceptado como herramienta de migraciones.
- Estrategia de perfiles `dev`, `test`, `desa`, `prod` aceptada.
- Estrategia de tests aceptada: H2 + PostgreSQL/Testcontainers selectivo.
- Convencion de `.env` y `.env.example` aceptada.
- Orden de PRs aceptado.
- Limite de PRs feature aceptado: objetivo unico, 8 a 15 archivos aproximados y maximo 2 endpoints.
- Criterio de scoping por `business_id` aceptado.
- Estrategia de contexto dev temporal aceptada.
- Riesgos de auth/cookies/CORS/CSRF identificados.
- No quedan dudas bloqueantes contra `schema-db-mvp.md`, `api-contracts-mvp.md`, `flujos-funcionales-mvp.md` y `auth/google-mvp.md`.

## Definition of Done de cada PR

- El PR compila.
- `./gradlew test` pasa.
- Incluye tests nuevos o actualizados cuando aplica.
- No mezcla cambios no relacionados.
- No supera el alcance definido sin dividirse o justificarlo.
- El PR describe que problema resuelve, endpoints afectados y como se probo.
- Mantiene contratos en `snake_case` para endpoints v1.
- No acepta `business_id` desde requests admin.
- No loguea datos sensibles.
