# Tracking de Implementacion Backend MVP

Actualizado: 2026-08-09

## Proposito

Este documento resume el avance real mergeado en `develop` contra el plan de migracion backend MVP.

No reemplaza:

- `plan-migracion-backend-mvp.md`
- Jira bajo la epica `TURN-1`
- Los criterios de aceptacion de cada subtarea

Sirve para saber rapidamente que ya esta implementado, que sigue y que no debe asumirse como completo todavia.

## Estado general

El backend ya avanzo desde la base single-business/H2 hacia la base MVP con PostgreSQL, Flyway, schema MVP, contexto dev, errores, request id/health y recursos admin v1.

Appointments admin ya tiene creacion, listado base, detalle, edicion y transiciones de estado. Sin embargo, la agenda diaria y la creacion aun no cumplen por completo el contrato MVP, por lo que `PR 5` sigue siendo la brecha funcional prioritaria.

La fuente de este estado es `develop` en el commit `97b91c7` (merge de PR #46, 2026-07-26). Las ramas remotas sin merge no se consideran implementadas en este tracking.

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

## Proximo foco recomendado

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

- PR 8 - Availability admin.
- PRs 9-18 - Gestion admin restante.
- PRs 19-20 - Auth Google y proteccion admin.
- PRs 21-24 - Booking publico y cancelacion.
- PR 25 - Hardening operativo MVP.

## Ramas remotas pendientes de integrar

Estas ramas contienen trabajo que no forma parte de `develop` y debe revisarse antes de considerarlo disponible:

- `feature/admin-availability-slots-v1`.
- `feature/admin-service-offerings-detail-edit-v1`.

Las ramas de tests de customer pendientes tampoco cambian el estado funcional del MVP.

## Verificacion tecnica observada

- `./gradlew test` compila el proyecto y ejecuta la suite.
- Los tests de aplicacion pasan; `FlywayMigrationTest` requiere un daemon Docker disponible para iniciar PostgreSQL con Testcontainers.
- En el entorno de esta revision no habia Docker disponible, por lo que ese test no pudo completarse. Antes de marcar la base tecnica como verificada localmente o en CI, debe ejecutarse la suite con Docker activo.

## Pendientes importantes no bloqueantes para PR 5

- Plan AWS (`plan-deploy-aws-mvp.md`) antes de preparar `desa`/`prod`.
- Auth Google, que entra despues de agenda/admin inicial.
- Algoritmo detallado de availability, que se define en implementacion con tests en PR 8/PR 22/PR 23.

## Nota para frontend

Frontend ya puede preparar UI y mocks contra:

- service offerings admin v1;
- staff members admin v1;
- staff-service offerings v1;
- customers admin v1;
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
