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

Appointments admin ya tiene creacion, listado base, detalle, edicion y transiciones de estado. Las brechas de lectura, escritura y availability se cerraran mediante TURN-90, TURN-105, TURN-109 y TURN-92 antes de conectar definitivamente el frontend.

La fuente de este estado es `develop` en el commit `4100520` (2026-09-03). Las ramas remotas sin merge no se consideran implementadas en este tracking.

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
| TURN-91 | Bug contenedor | Asegurar invariantes de escritura mediante TURN-105 y TURN-109 | TURN-72 y TURN-74 |
| TURN-92 | Bug | Converger availability admin con el contrato | TURN-71, TURN-72 y TURN-74 |
| TURN-93 | Story contenedora | Entregar dashboard mediante TURN-106, TURN-108 y TURN-107 | TURN-87 |

Los tickets terminados TURN-41, TURN-55, TURN-56 y TURN-57 conservan su historial y estan relacionados con los bugs que completan sus criterios pendientes.

### Desbloquear integracion frontend de auth

Antes de cerrar TURN-69 frontend mediante TURN-88:

- alinear request, responses, cookie y roles con el contrato canonico de `api-contracts-mvp.md`;
- hacer logout idempotente;
- documentar/aplicar un aprovisionamiento local repetible para un OWNER real.

TURN-89 debe incluir `/api/v1/business-hours/**` en la proteccion admin antes de TURN-82. Ambas correcciones son acotadas y pueden avanzar en paralelo a las brechas de appointments que desbloquean la agenda.

### Cerrar brechas de appointments admin

Jira ejecutable:

- `TURN-90`: lectura de agenda y DTO enriquecido.
- `TURN-105`: invariantes al crear turnos admin.
- `TURN-109`: invariantes al editar o reprogramar.
- `TURN-92`: contrato de availability admin.

Avance ya mergeado:

- Migrar appointments admin a `/api/v1/appointments`.
- Permitir crear turno manual.
- Validar customer, service y staff dentro del negocio.
- Validar no solapamiento base.
- Obtener detalle y actualizar parcialmente un turno.
- Confirmar, cancelar, completar y marcar un turno como no-show, con transiciones de estado validadas.

Pendiente para considerar el flujo admin completo:

- Permitir listar agenda diaria con filtros del contrato (`date` o `from/to`, `status`, `staff_member_id`, etc.).
- Aceptar cliente existente o cliente rapido segun contrato.
- Calcular `ends_at`, `duration_minutes` y `price_cents` en backend.
- Validar staff-service.
- Validar no solapamiento solo para estados bloqueantes segun reglas MVP.
- Persistir/default `source = ADMIN` para creacion admin.
- Alinear response de appointment con el contrato MVP si frontend necesita datos embebidos de cliente, servicio y profesional.

El detalle, la edicion y las transiciones ya existen en `develop`, pero deben revisarse contra TURN-109 y los criterios de los PRs 6, 7 y 7b antes de marcarlos como completados en Jira.

### Hardening operativo final

TURN-32 es la historia contenedora y permanece `To Do`. No representa un unico PR. Su secuencia ejecutable es:

| Jira | Unidad de entrega | Puede comenzar cuando |
| --- | --- | --- |
| TURN-62 | Boundary BFF, CORS y CSRF | Puede abordarse ahora |
| TURN-110 | Cleanup de sesiones | TURN-88 cerrado |
| TURN-111 | Cleanup de tokens publicos | TURN-61 cerrado |
| TURN-112 | Rate limiting publico | TURN-58 a TURN-61 cerrados |
| TURN-113 | Concurrencia real de reservas | TURN-105, TURN-109 y TURN-60 cerrados |
| TURN-114 | Auditoria de indices | Agenda, availability, dashboard y booking estabilizados |
| TURN-116 | Auditoria de datos sensibles en logs | TURN-88, TURN-60 y TURN-61 cerrados |
| TURN-115 | Runbook operativo final | Las otras subtareas de TURN-32 cerradas |

Cada subtarea corresponde, en lo posible, a un PR. Los criterios detallados y el estado de asignacion viven en Jira.

## No asumir todavia como completo

No marcar como completos sin revisar sus criterios de aceptacion:

- TURN-90, TURN-105, TURN-109 y TURN-92 - Agenda, escritura y availability contra el contrato MVP.
- PR 6 - Appointments admin: detalle y edicion.
- PR 7 - Appointments admin: confirmar y cancelar.
- PR 7b - Appointments admin: completar y marcar no-show.

Pendiente de implementar:

- TURN-58 a TURN-61 - Booking publico y cancelacion.
- TURN-32 mediante TURN-62 y TURN-110 a TURN-116 - Hardening operativo MVP.

PRs 19-20 ya estan mergeados, pero conservan las brechas contractuales y de hardening registradas arriba. El codigo de PRs 8-15 esta presente en `develop`, pero debe revisarse contra su contrato y criterios Jira antes de marcar cada ticket como completado. PRs 16-18 ya estan mergeados y no deben figurar como pendientes de implementacion.

## Ramas remotas pendientes de integrar

Las ramas funcionales que antes se registraban para availability y detalle/edicion de services ya no estan pendientes: sus implementaciones estan presentes en `develop`.

Las ramas remotas de tests o documentacion no cambian el estado funcional del MVP hasta que se integren.

## Verificacion tecnica observada

- `./gradlew test` compila el proyecto y ejecuta la suite.
- Los tests de aplicacion pasan; `FlywayMigrationTest` requiere un daemon Docker disponible para iniciar PostgreSQL con Testcontainers.
- En el entorno de esta revision no habia Docker disponible, por lo que ese test no pudo completarse. Antes de marcar la base tecnica como verificada localmente o en CI, debe ejecutarse la suite con Docker activo.

## Pendientes importantes en paralelo

- Plan AWS (`plan-deploy-aws-mvp.md`) antes de preparar `desa`/`prod`.
- TURN-88 de convergencia auth, que bloquea TURN-69 frontend y puede resolverse en paralelo a appointments.
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

Para conectar agenda diaria real, esperar a TURN-90; para crear o editar con invariantes completas, esperar a TURN-105, TURN-109 y TURN-92.

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
