# Turnero API - Proximos pasos MVP

## Proposito

Este documento ordena los proximos pasos para llevar el backend actual hacia el MVP definido por:

- Pantallas aprobadas en Stitch.
- Esquema de DB documentado en `schema-db-mvp.md`.
- Flujos funcionales documentados en `flujos-funcionales-mvp.md`.
- Login con Google documentado en `auth/google-mvp.md`.
- Caso inicial de negocio: tiendas chicas, con un owner que puede o no atender turnos.
- Deploy previsto en AWS.

La meta es evitar empezar a codear controllers/entities sin antes cerrar flujos, contratos y migraciones.

## Estado actual

### Listo

- MVP visual principal cerrado en Stitch.
- Esquema DB MVP documentado en `schema-db-mvp.md`.
- Flujos funcionales MVP aprobados en `flujos-funcionales-mvp.md`.
- Decision de Google Auth MVP documentada en `auth/google-mvp.md`.
- Contratos HTTP MVP aprobados en `api-contracts-mvp.md`.
- Plan de migracion backend MVP documentado en `plan-migracion-backend-mvp.md`, con PRs chicos tipo subtarea Jira y excepciones de arquitectura explicitadas.
- Backend actual compila y la suite de tests pasa.
- Ya existen capas base:
  - Controllers.
  - Services.
  - Repositories.
  - Entities.
  - DTOs request/response.
  - Mappers MapStruct.
  - Tests unitarios e integracion.
- Decisiones principales cerradas:
  - Login owner con Google.
  - Booking cliente sin login obligatorio.
  - `businesses.onboarding_status`.
  - `staff_working_hours` desde el principio.
  - `appointments.price_cents` como snapshot.
  - `appointments.staff_member_id` obligatorio.
  - `Cualquiera disponible` resuelto por logica backend antes de guardar.
  - Cancelacion publica con token seguro.
  - `notifications` como tabla futura/post-MVP.

### Pendiente

- Estrategia de disponibilidad.
- Seguridad/autorizacion avanzada si excede lo definido en contratos.
- Plan de deploy AWS.
- Versionado de schema con Flyway.
- Entorno local de DB con Docker para PostgreSQL.
- Manejo de variables de entorno con `.env` local y `.env.example` versionado.
- Definicion de ambientes/perfiles `dev`, `test`, `desa` y futuro `prod`.
- Robustez backend transversal: observabilidad, seguridad, concurrencia, tests, performance y operacion minima.
- Migracion desde H2/dev hacia PostgreSQL como DB objetivo.

### Backend actual observado

Estado: base funcional single-business.

El backend actual ya cubre:

- CRUD base de appointments, customers, service offerings y staff members.
- DTOs separados para request/response.
- Validaciones basicas con Bean Validation.
- Manejo global de errores.
- Auditoria parcial con `createdAt`/`updatedAt`.
- Validacion de referencias en appointments.
- Validacion de solapamiento por `staffMemberId`.
- Tests unitarios e integracion.

Brechas contra `schema-db-mvp.md`:

- No existe `Business`.
- No existe `User`.
- No existe scoping por `business_id`.
- No existen relaciones JPA reales entre appointment/customer/service/staff.
- `Appointment` usa `dateTime` y `durationMinutes`, no `starts_at`/`ends_at`.
- `ServiceOffering.price` usa `double`, no `price_cents`.
- No existen `BookingSettings`, `BusinessHours`, `StaffWorkingHours`, `AvailabilityException` ni `AppointmentPublicToken`.
- No hay Flyway.
- No hay PostgreSQL configurado como DB real.
- No hay auth Google ni autorizacion por roles.
- No hay endpoints publicos de booking/cancelacion.

## Roadmap de trabajo

### 1. Flujos funcionales MVP

Estado: Aprobado. Mantenerlo como fuente de comportamiento funcional.

Objetivo:

Definir paso a paso como se comporta el sistema en los flujos principales antes de escribir endpoints.

Flujos a documentar:

- Alta/provisioning de negocio.
- Login owner con Google.
- Configuracion inicial del negocio.
- Crear profesional.
- Crear servicio.
- Asociar servicios a profesional.
- Crear turno desde admin.
- Editar turno desde admin.
- Confirmar turno pendiente.
- Booking publico.
- Cancelacion publica con token.
- Estados vacios de primer uso.

Documento:

- `flujos-funcionales-mvp.md`

### 2. Contratos de API

Estado: Aprobado en `api-contracts-mvp.md`.

Objetivo:

Definir endpoints, DTOs, filtros, respuestas y errores antes de implementar controllers.

Recursos principales:

- Auth/session.
- Businesses.
- Booking settings.
- Business hours.
- Staff members.
- Staff working hours.
- Service offerings.
- Staff service offerings.
- Customers.
- Appointments.
- Availability.
- Public booking.
- Public cancellation token.

Temas a cerrar:

- URL base y versionado, por ejemplo `/api/v1`.
- Request DTOs.
- Response DTOs.
- Validaciones.
- Paginacion y filtros.
- Formato de errores.
- Scoping por `business_id`.
- Endpoints publicos vs endpoints autenticados.

Output esperado:

- Contrato legible para frontend/backend.
- Lista de endpoints MVP.
- DTOs principales.
- Ejemplos de request/response.

Documento:

- `api-contracts-mvp.md`

### 3. Plan de migracion backend

Estado: Documentado en `plan-migracion-backend-mvp.md`.

Objetivo:

Definir como pasar del backend actual a la nueva arquitectura sin mezclar cambios sin control.

Backend actual:

- Spring Boot.
- JPA.
- H2/MySQL configurable.
- Entidades actuales:
  - `Customer`
  - `ServiceOffering`
  - `StaffMember`
  - `Appointment`
- `Appointment` hoy usa IDs planos y no relaciones JPA reales.

Cambios esperados:

- Introducir `Business`.
- Introducir `User`.
- Agregar `business_id` a entidades operativas.
- Agregar `BookingSettings`.
- Agregar `BusinessHours`.
- Agregar `StaffWorkingHours`.
- Agregar `AvailabilityException`.
- Agregar `AppointmentPublicToken`.
- Mantener `Notification` como futura/post-MVP.
- Separar DTOs request/response.
- Agregar Flyway.
- Agregar entorno local con Docker Compose para PostgreSQL.
- Agregar Makefile local para levantar DB, correr la app y correr tests.
- Agregar `.env.example` con variables requeridas y usar `.env` local no versionado.
- Definir perfiles Spring y configuracion por ambiente:
  - `dev`: desarrollo local con PostgreSQL en Docker.
  - `test`: tests automatizados con H2 para casos rapidos y PostgreSQL/Testcontainers selectivo para Flyway, constraints y concurrencia.
  - `desa`: ambiente compartido de desarrollo con secretos fuera del repo.
  - `prod`: queda para el plan AWS final.
- Incorporar robustez/escalabilidad durante la migracion:
  - Observabilidad minima: logs utiles, request id y health checks.
  - Seguridad: CORS por ambiente, CSRF si aplica, no loguear tokens/cookies y rate limiting en endpoints publicos.
  - Concurrencia: prevenir doble reserva con transacciones, constraints o locking donde corresponda.
  - Validaciones y errores: contrato estable y transiciones de estado centralizadas.
  - DB: migraciones versionadas, indices para consultas principales y constraints.
  - Tests: scoping por `business_id`, auth/session, availability, conflictos y booking publico.
  - Performance: evitar N+1, limitar rangos de availability y paginar listados grandes.
  - Operacion: cleanup de sesiones/tokens vencidos, backups y estrategia de rollback.
- Usar PostgreSQL como base real objetivo para AWS.

Output esperado:

- Orden de subtareas/PRs.
- Riesgos de migracion.
- Entidades a crear/modificar.
- Estrategia de DB local con Docker y seed dev alineado al frontend.
- Variables de entorno requeridas para DB, auth, cookies y CORS.
- Configuracion esperada por ambiente/perfil.
- Checklist de robustez/escalabilidad mapeado a PRs.
- Tests esperados por etapa.
- Limites de alcance para PRs feature: objetivo unico, cambios revisables y hasta 2 endpoints.

Documento:

- `plan-migracion-backend-mvp.md`

### 4. Disponibilidad y agenda

Estado: Planificado en `plan-migracion-backend-mvp.md`.

Objetivo:

Implementar la logica de generacion de slots, validacion de solapamientos y asignacion automatica para `Cualquiera disponible` dentro de los PRs correspondientes.

Reglas a cubrir:

- Usar `business_hours` como disponibilidad base del negocio.
- Usar `staff_working_hours` como disponibilidad del profesional.
- Aplicar `availability_exceptions`.
- Excluir horarios ocupados por appointments `PENDING` o `CONFIRMED`.
- Usar `booking_settings.slot_interval_minutes`.
- Respetar `booking_settings.min_notice_hours`.
- Respetar `booking_settings.booking_window_days`.
- Resolver `Cualquiera disponible` asignando un profesional antes de crear el appointment.

Implementacion planificada:

- PR 5: solapamiento y validacion al crear appointment admin.
- PR 8: availability admin para crear/editar turnos.
- PR 22: availability publica para booking.
- PR 23: revalidacion de disponibilidad y asignacion de `Cualquiera disponible` al crear reserva publica.

Decision:

- No crear un documento propio para bajar el algoritmo con mas detalle.
- El dev que implemente los PRs define el detalle del algoritmo durante la implementacion.
- La implementacion debe apoyarse en `flujos-funcionales-mvp.md`, `api-contracts-mvp.md`, `schema-db-mvp.md` y tests.
- Availability es informativa; crear o editar appointments debe revalidar disponibilidad dentro del flujo de escritura.

### 5. Seguridad y autorizacion

Estado: Parcial. Auth Google vive en `auth/google-mvp.md` y autorizacion/scoping por endpoint vive en `api-contracts-mvp.md`.

Google Auth MVP esta documentado en `auth/google-mvp.md`. El contrato HTTP ya baja endpoints admin/publicos, scoping por `business_id` y reglas de tokens publicos. Puede hacer falta un documento propio si se define CSRF, rotacion de sesiones, permisos por roles no OWNER o politica de secretos para deploy.

Objetivo:

Definir como se protege el backend y como se separan datos por negocio.

Temas a cubrir:

- Login con Google para owner.
- Validacion de identidad.
- Roles iniciales.
- Scoping obligatorio por `business_id`.
- Endpoints admin autenticados.
- Endpoints publicos de booking.
- Cancelacion publica con token hasheado.
- No usar `appointment_id` como autorizacion publica.
- Manejo de secrets.

Output esperado:

- Modelo de auth para MVP.
- Reglas de autorizacion por endpoint.
- Politica de tokens publicos.

Puede vivir en:

- `api-contracts-mvp.md`
- o documento propio `seguridad-mvp.md` si hace falta.

### 6. Plan AWS

Estado: Pendiente, no bloqueante para iniciar implementacion local.

Objetivo:

Definir deploy objetivo antes de preparar ambiente compartido `desa`, `prod`, secretos reales y operacion productiva.

Decision:

- No bloquea empezar a codear backend localmente.
- No bloquea adaptar los PRs de `plan-migracion-backend-mvp.md` a historias/subtareas Jira.
- Si bloquea decisiones de despliegue, secretos, backups, rollback y operacion de ambientes compartidos.

Recomendacion inicial:

- Backend: Spring Boot en ECS/Fargate o Elastic Beanstalk.
- DB: RDS PostgreSQL.
- Secrets: AWS Secrets Manager o SSM Parameter Store.
- Logs: CloudWatch.
- CI/CD: GitHub Actions.
- Frontend: definir si va full AWS o se permite Vercel.

Temas a cerrar:

- Servicio elegido para backend.
- Region.
- Ambientes: dev/staging/prod.
- Relacion entre perfiles backend `dev`, `test`, `desa`, `prod` y ambientes desplegados.
- Estrategia de migraciones.
- Imagen/version de PostgreSQL alineada con desarrollo local.
- Variables de entorno y `.env.example`.
- CORS.
- Dominio/API Gateway/Load Balancer.
- Backups DB.

Output esperado:

- Arquitectura AWS MVP.
- Servicios elegidos.
- Variables/secrets requeridos.
- Pipeline minimo.

Documento sugerido:

- `plan-deploy-aws-mvp.md`

## Orden recomendado inmediato

1. Revisar y aceptar `plan-migracion-backend-mvp.md`.
2. Adaptar los PRs del plan de migracion a historias/subtareas Jira.
3. Empezar implementacion backend local con los primeros PRs del plan.
4. Crear `plan-deploy-aws-mvp.md` antes de preparar `desa`/`prod`.

## Definition of Ready para codear

Antes de codear backend, deberia estar definido:

- DB schema MVP aceptado.
- Flujos funcionales principales aceptados.
- Contratos de endpoints MVP aceptados.
- Plan de migracion documentado y aceptado.
- Base de datos objetivo definida: PostgreSQL.
- Estrategia local/dev definida: Docker Compose, Makefile local, perfiles, `.env.example` y Flyway.
- Primeras historias/subtareas Jira derivadas del plan de migracion.

No bloquea inicio de implementacion local:

- Plan AWS final.
- Backups y rollback productivo.
- Secrets reales de `desa`/`prod`.
- CI/CD definitivo.

## Notas

- No implementar onboarding self-service publico en MVP.
- El alta de negocio se maneja como provisioning interno/controlado.
- El frontend se va a implementar contra contratos, no contra supuestos visuales sueltos.
- Stitch sigue siendo fuente visual; `schema-db-mvp.md` y los contratos seran fuente tecnica.
