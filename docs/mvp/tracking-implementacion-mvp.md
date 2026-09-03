# Tracking de Implementacion Backend MVP

Actualizado: 2026-09-03

## Proposito

Este documento resume el avance real mergeado en `develop` contra el plan de migracion backend MVP.

No reemplaza:

- `plan-migracion-backend-mvp.md`
- Jira bajo la epica `TURN-1`
- Los criterios de aceptacion de cada subtarea

Sirve para saber rapidamente que ya esta implementado, que sigue y que no debe asumirse como completo todavia.

## Estado general

El backend ya avanzo desde la base single-business/H2 hacia la base MVP con PostgreSQL, Flyway, schema MVP, errores, request id/health, recursos admin v1 y autenticacion Google con sesion propia. El contexto de business normal ya sale del usuario autenticado.

Appointments admin ya tiene creacion, listado base, detalle, edicion y transiciones de estado. Sin embargo, la agenda diaria y la creacion aun no cumplen por completo el contrato MVP, por lo que `PR 5` sigue siendo la brecha funcional prioritaria.

La fuente de este estado es `develop` en el commit `ad37752` (merge de PR #62, 2026-09-02). Las ramas remotas sin merge no se consideran implementadas en este tracking.

## PRs completados y avances en develop

| Plan | Jira | Estado | Evidencia en develop |
| --- | --- | --- | --- |
| PR A1 | TURN-33 | Completado | PostgreSQL local, Docker Compose, Flyway, schema inicial, entidades/repositories, seed dev e infrastructure tests mergeados entre PRs #31-#35 |
| PR A2 | TURN-34 | Completado | `CurrentBusinessContext` y contexto dev/test mergeados en PR #36 |
| PR A3 | TURN-35 | Completado | Contrato de errores MVP mergeado en PR #37 |
| PR A4 | TURN-36 | Completado | Request id, logging y health checks mergeados en PR #38 |
| PR 1 | TURN-37 | Completado | Service offerings admin v1 mergeado en PR #39 |
| PR 2 | TURN-38 | Completado | Staff members admin v1 mergeado en PR #40 |
| PR 3 | TURN-39 | Completado | Staff-service offerings v1 mergeado en PR #41 |
| PR 4 | TURN-40 | Completado | Customers admin v1 mergeado en PR #42 |
| PR 5 | TURN-41 | Parcial | Appointments admin movido a `/api/v1/appointments` y creacion/listado base mergeados en PR #43 |
| PR 6 | TURN-42 | Implementado; pendiente validar alcance MVP | Detalle y actualizacion parcial de appointments mergeados en PR #44 |
| PR 7 | TURN-43 | Implementado; pendiente validar alcance MVP | Acciones de confirmar y cancelar appointments mergeadas en PR #45 |
| PR 7b | TURN-44 | Implementado; pendiente validar alcance MVP | Acciones de completar y marcar no-show mergeadas en PR #46 |
| PR 8 | - | Implementado; pendiente validar contrato MVP | `GET /api/v1/availability/slots` y `AvailabilityService` ya usan horarios de negocio/profesional, excepciones y appointments bloqueantes |
| PR 9 | - | Implementado; pendiente validar alcance MVP | Detalle y edicion de service offerings en `/api/v1/service-offerings/{id}` |
| PR 10 | - | Implementado; pendiente validar alcance MVP | Baja de service offerings en `/api/v1/service-offerings/{id}` |
| PR 11 | - | Implementado; pendiente validar alcance MVP | Detalle y edicion de staff members en `/api/v1/staff-members/{id}` |
| PR 12 | - | Implementado; pendiente validar alcance MVP | Baja de staff members en `/api/v1/staff-members/{id}` |
| PR 13 | - | Implementado; pendiente validar alcance MVP | Lectura y reemplazo de `staff_working_hours` por profesional |
| PR 14 | - | Implementado; pendiente validar alcance MVP | Detalle y edicion de customers en `/api/v1/customers/{id}` |
| PR 15 | - | Implementado; pendiente validar alcance MVP | Baja de customers en `/api/v1/customers/{id}` |
| PR 16 | TURN-53 | Completado | Datos base del negocio actual en `GET/PATCH /api/v1/business` mergeados en PR #54 |
| PR 17 | TURN-54 | Completado | Booking settings en `GET/PATCH /api/v1/booking-settings` mergeados en PR #59 |
| PR 18 | TURN-55 | Completado | Horarios semanales del negocio en `GET/PUT /api/v1/business-hours` mergeados en PR #60 |
| PR 19 | - | Implementado; pendiente converger contrato | Login Google, sesion propia y `/auth/me` mergeados en PR #61; el wire contract actual difiere de `api-contracts-mvp.md` |
| PR 20 | - | Implementado; pendiente hardening | Logout, interceptor admin y business desde usuario autenticado mergeados en PR #62; falta proteger `business-hours` y cerrar logout idempotente |

## Proximo foco recomendado

### Dependencias Jira creadas para integracion frontend

La reorganizacion de TURN-68 registro las brechas backend como trabajo separado bajo la epica TURN-1:

| Jira | Tipo | Objetivo | Bloquea |
| --- | --- | --- | --- |
| TURN-88 | Bug | Converger auth/sesion con el contrato MVP | TURN-69 |
| TURN-89 | Bug | Proteger business-hours con autenticacion admin | TURN-82 |
| TURN-90 | Bug | Completar lectura de agenda y DTO enriquecido | TURN-70 y TURN-73 |
| TURN-91 | Bug | Asegurar invariantes de creacion/edicion de appointments | TURN-72 y TURN-74 |
| TURN-92 | Bug | Converger availability admin con el contrato | TURN-71, TURN-72 y TURN-74 |
| TURN-93 | Story | Exponer resumen operativo de dashboard | TURN-87 |

Los tickets terminados TURN-41, TURN-55, TURN-56 y TURN-57 conservan su historial y estan relacionados con los bugs que completan sus criterios pendientes.

### Desbloquear integracion frontend de auth

Antes de cerrar TURN-69 frontend mediante TURN-88:

- alinear request, responses, cookie y roles con el contrato canonico de `api-contracts-mvp.md`;
- incluir `/api/v1/business-hours/**` en la proteccion admin;
- hacer logout idempotente;
- documentar/aplicar un aprovisionamiento local repetible para un OWNER real.

Estas correcciones son acotadas y preceden a la integracion de configuracion. El siguiente foco funcional backend sigue siendo cerrar PR 5.

### Cerrar PR 5 - Appointments admin: agenda diaria y crear turno

Jira: `TURN-41`

Avance ya mergeado:

- Migrar appointments admin a `/api/v1/appointments`.
- Permitir crear turno manual.
- Validar customer, service y staff dentro del negocio.
- Validar no solapamiento base.
- Obtener detalle y actualizar parcialmente un turno.
- Confirmar, cancelar, completar y marcar un turno como no-show, con transiciones de estado validadas.

Pendiente para considerar `PR 5` completo:

- Permitir listar agenda diaria con filtros del contrato (`date` o `from/to`, `status`, `staff_member_id`, etc.).
- Aceptar cliente existente o cliente rapido segun contrato.
- Calcular `ends_at`, `duration_minutes` y `price_cents` en backend.
- Validar staff-service.
- Validar no solapamiento solo para estados bloqueantes segun reglas MVP.
- Persistir/default `source = ADMIN` para creacion admin.
- Alinear response de appointment con el contrato MVP si frontend necesita datos embebidos de cliente, servicio y profesional.

El detalle, la edicion y las transiciones ya existen en `develop`, pero deben revisarse contra los criterios de los PRs 6, 7 y 7b antes de marcarlos como completados en Jira.

## No asumir todavia como completo

No marcar como completos sin revisar sus criterios de aceptacion:

- PR 5 - Agenda diaria y creacion de appointments contra el contrato MVP.
- PR 6 - Appointments admin: detalle y edicion.
- PR 7 - Appointments admin: confirmar y cancelar.
- PR 7b - Appointments admin: completar y marcar no-show.

Pendiente de implementar:

- PRs 21-24 - Booking publico y cancelacion.
- PR 25 - Hardening operativo MVP.

PRs 19-20 ya estan mergeados, pero conservan las brechas contractuales y de hardening registradas arriba. El codigo de PRs 8-15 esta presente en `develop`, pero debe revisarse contra su contrato y criterios Jira antes de marcar cada ticket como completado. PRs 16-18 ya estan mergeados y no deben figurar como pendientes de implementacion.

## Ramas remotas pendientes de integrar

Las ramas funcionales que antes se registraban para availability y detalle/edicion de services ya no estan pendientes: sus implementaciones estan presentes en `develop`.

Las ramas remotas de tests o documentacion no cambian el estado funcional del MVP hasta que se integren.

## Verificacion tecnica observada

- `./gradlew test` compila el proyecto y ejecuta la suite.
- Los tests de aplicacion pasan; `FlywayMigrationTest` requiere un daemon Docker disponible para iniciar PostgreSQL con Testcontainers.
- En el entorno de esta revision no habia Docker disponible, por lo que ese test no pudo completarse. Antes de marcar la base tecnica como verificada localmente o en CI, debe ejecutarse la suite con Docker activo.

## Pendientes importantes no bloqueantes para PR 5

- Plan AWS (`plan-deploy-aws-mvp.md`) antes de preparar `desa`/`prod`.
- TURN-88 de convergencia auth, que bloquea TURN-69 frontend y puede resolverse en paralelo al cierre de PR 5.
- Validar el contrato definitivo de Availability: el endpoint existe, pero su response y casos de rango deben revisarse contra `api-contracts-mvp.md` antes de dar PR 8 por cerrado.

## Nota para frontend

Frontend ya puede preparar UI y mocks contra:

- service offerings admin v1;
- staff members admin v1;
- staff-service offerings v1;
- customers admin v1;
- business y booking settings;
- horarios del negocio y de profesionales;
- availability admin, sujeto a la validacion pendiente de su contrato;
- appointments admin create/list base.

Para conectar agenda diaria real, esperar a que `PR 5` cierre los filtros/rango de agenda y el response necesario para pintar cliente, servicio y profesional sin joins manuales en frontend.

## Comandos de referencia

Para verificar base local:

```bash
make db-up
make run
make test
make db-down
```

Suite general:

```bash
./gradlew test
```
