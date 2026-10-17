# Tracking de Implementacion Backend MVP

Actualizado: 2026-10-16

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

La base histórica de este estado es `develop` en el commit `8b29021` (2026-09-25). La entrega TURN-76 está integrada en `a734b63` (2026-10-02). Las ramas remotas sin merge no se consideran implementadas en este tracking.

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
| PR 19 | TURN-88 | Completado | Login Google, sesión propia y `/auth/me` mergeados en PR #61; contrato canónico alineado en `3533fe7` y aprovisionamiento local repetible mergeado en PR #65 |
| PR 20 | TURN-89 | Completado | Logout, interceptor admin y business desde usuario autenticado mergeados en PR #62; `business-hours` atraviesa la protección admin y tiene cobertura de sesión, rol y scoping |
| PR 21 | TURN-58 | Completado | Perfil público y servicios reservables expuestos en PR #63 |
| Fix horarios | TURN-82 / TURN-55 | Completado | PR #69 actualiza los días existentes en el lugar, crea solo los faltantes y conserva IDs; cubre persistencia real en PostgreSQL sin violar `uq_business_hours_day` |
| Listado real de servicios | TURN-76 | Entregado; cierre Jira a cargo del usuario | [PR #72](https://github.com/Candela-98/Turnero-api/pull/72), merge `a734b63`: filtros, paginación, categorías y política de ordenamiento |

## Proximo foco recomendado

### Dependencias Jira creadas para integracion frontend

La reorganizacion de TURN-68 registro las brechas backend como trabajo separado bajo la epica TURN-1:

| Jira | Tipo | Objetivo | Bloquea |
| --- | --- | --- | --- |
| TURN-88 | Completado | Converger auth/sesion con el contrato MVP | TURN-69 |
| TURN-89 | Completado | Proteger business-hours con autenticacion admin | TURN-82 |
| TURN-90 | Bug | Completar lectura de agenda y DTO enriquecido | TURN-70 y TURN-73 |
| TURN-91 | Bug contenedor | Asegurar invariantes de escritura mediante TURN-105 y TURN-109 | TURN-72 y TURN-74 |
| TURN-92 | Bug | Converger availability admin con el contrato | TURN-71, TURN-72 y TURN-74 |
| TURN-93 | Story contenedora | Entregar dashboard mediante TURN-106, TURN-108 y TURN-107 | TURN-87 |

Los tickets terminados TURN-41, TURN-55, TURN-56 y TURN-57 conservan su historial y estan relacionados con los bugs que completan sus criterios pendientes.

### Integracion frontend de auth y horarios

TURN-88 alineó request, responses, cookie y roles con el contrato canónico. El aprovisionamiento local de un OWNER real quedó mergeado en PR #65; la branch revisada para TURN-62 ya contiene logout idempotente en AuthController. Esta observación de la branch no acredita un merge adicional ni un cierre en Jira; la mitigación de procedencia del BFF sigue pendiente.

TURN-89 incorporó `/api/v1/business-hours/**` a la protección admin y verifica sesión, rol OWNER y scoping entre negocios. Con PR #69, el `PUT` de semana completa de TURN-82 actualiza filas existentes en lugar de reinsertarlas; TURN-82 y su historia padre TURN-85 ya están en Done en Jira.

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
| TURN-62 | Decision documental de boundary BFF, CORS y CSRF | Alcance aprobado; entrega y cierre pendientes |
| TURN-110 | Cleanup de sesiones | TURN-88 cerrado |
| TURN-111 | Cleanup de tokens publicos | TURN-61 cerrado |
| TURN-112 | Rate limiting publico | TURN-58 a TURN-61 cerrados |
| TURN-113 | Concurrencia real de reservas | TURN-105, TURN-109 y TURN-60 cerrados |
| TURN-114 | Auditoria de indices | Agenda, availability, dashboard y booking estabilizados |
| TURN-116 | Auditoria de datos sensibles en logs | TURN-88, TURN-60 y TURN-61 cerrados |
| TURN-115 | Runbook operativo final | Las otras subtareas de TURN-32 cerradas |

Cada subtarea corresponde, en lo posible, a un PR. Los criterios detallados y el estado de asignacion viven en Jira.

TURN-62 tiene alcance documental aprobado; todavia no esta mergeado ni cerrado. La decision vive en [el contrato canonico](api-contracts-mvp.md#frontera-http-bff-cors-y-csrf-turn-62). La mitigacion explicita de procedencia/CSRF del BFF todavia NO esta implementada y requiere una subtarea frontend/BFF pendiente de crear/vincular, sin ID confirmado. El cierre documental no completa esa implementacion ni TURN-32. Actualizar el estado de entrega al mergearse el PR y verificar el cierre en Jira.

## No asumir todavia como completo

No marcar como completos sin revisar sus criterios de aceptacion:

- TURN-90, TURN-105, TURN-109 y TURN-92 - Agenda, escritura y availability contra el contrato MVP.
- PR 6 - Appointments admin: detalle y edicion.
- PR 7 - Appointments admin: confirmar y cancelar.
- PR 7b - Appointments admin: completar y marcar no-show.

Pendiente de implementar:

- TURN-59 a TURN-61 - Availability, creación y cancelación de booking público.
- Mitigacion de procedencia/CSRF en BFF - Subtarea frontend/BFF pendiente de crear/vincular.
- TURN-32 - Decision documental TURN-62 pendiente de entrega/cierre, implementacion BFF separada y hardening TURN-110 a TURN-116.

PRs 19-20 ya estan mergeados; TURN-88 y TURN-89 figuran completados en este tracking. El hardening HTTP y la implementacion de mitigacion en el BFF se siguen por separado segun lo indicado arriba. El codigo de PRs 8-15 esta presente en `develop`, pero debe revisarse contra su contrato y criterios Jira antes de marcar cada ticket como completado. PRs 16-18 ya estan mergeados y no deben figurar como pendientes de implementacion.

## Ramas remotas pendientes de integrar

Las ramas funcionales que antes se registraban para availability y detalle/edicion de services ya no estan pendientes: sus implementaciones estan presentes en `develop`.

Las ramas remotas de tests o documentacion no cambian el estado funcional del MVP hasta que se integren.

## Verificacion tecnica observada

- `./gradlew test` compila el proyecto y ejecuta la suite.
- Los tests de aplicacion pasan; `FlywayMigrationTest` requiere un daemon Docker disponible para iniciar PostgreSQL con Testcontainers.
- En el entorno de esta revision no habia Docker disponible, por lo que ese test no pudo completarse. Antes de marcar la base tecnica como verificada localmente o en CI, debe ejecutarse la suite con Docker activo.

## Pendientes importantes en paralelo

- Plan AWS (`plan-deploy-aws-mvp.md`) antes de preparar `desa`/`prod`.
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

## TURN-76 integrada — listado real de servicios

Integrada el 2026-10-02 en `develop` mediante [PR #72](https://github.com/Candela-98/Turnero-api/pull/72), merge `a734b63`. El frontend dependiente quedó integrado en `dev` mediante [PR #9](https://github.com/Candela-98/Turnero-frontend/pull/9), merge `70df540`.

- Listado paginado real `{ data, page }`, búsqueda por nombre/categoría y filtros combinados por categoría y estado.
- Parámetros validados, orden estable con desempate por ID y aislamiento por negocio autenticado.
- Endpoint de categorías distintas para el negocio actual.
- `ServiceOfferingSort` centraliza campos permitidos, traducción a propiedades Java, direcciones y orden predeterminado; el servicio recibe un `Sort` resuelto.
- Contrato documentado y cobertura de controller/integración para filtros, paginación, categorías y aislamiento.
- Verificación local inicial: 354 tests pasaron; `BusinessHoursPostgresIT` y `FlywayMigrationTest` no pudieron iniciar por falta de Docker. Después del refactor pasaron los 58 tests afectados, incluidos los nuevos de ordenamiento.
- CI del PR #72 completado correctamente antes del merge. La limitación de Docker registrada arriba corresponde al entorno local, no a un fallo pendiente de CI.
- Entrega técnica completada y validación local aprobada por el usuario. El usuario realiza el cierre de TURN-76 en Jira.

## Correcciones backend para TURN-96 (2026-10-12)

Pendientes de integración; no cambian el estado mergeado del tracking:

- `ServOfferingServiceImpl` asigna `createdAt`/`updatedAt` al crear y actualiza `updatedAt` al editar/desactivar. Prueba de persistencia con timestamps NOT NULL.
- `V1001__sync_seed_sequences.sql`, exclusivamente en seeds locales, sincroniza las secuencias de las cuatro tablas con IDs explícitos de V1000. Preserva filas y contadores adelantados; no modifica el seed ya aplicado. Probado en PostgreSQL con bases nuevas y existentes y aplicado en la base local de esta sesión.
- Suite completa con `make test`: 440 tests aprobados sin omisiones. Pruebas manuales de alta/edición aprobadas por el usuario en desktop y mobile; PR backend [#75](https://github.com/Candela-98/Turnero-api/pull/75) abierto hacia `develop`, relacionado con frontend [#10](https://github.com/Candela-98/Turnero-frontend/pull/10). Pendiente de merge.


## TURN-98 — eliminación del catálogo (2026-10-13)

Implementación local en `feature/TURN-98-service-removal`, basada en TURN-96. QA manual aprobado por el usuario el 2026-10-16. Preparada para PR sobre `fix/TURN-96-service-persistence` mientras #75 siga abierto; cambiar la base a `develop` después de integrar TURN-96. Integrar backend TURN-98 antes del frontend que utiliza la eliminación.

- `DELETE /api/v1/service-offerings/{id}/catalog` permite borrado lógico solo de servicios inactivos; devuelve 409 para activos y 404 para eliminados/otro negocio. DELETE existente conserva su función de desactivar.
- Migración `V1002__service_offering_logical_removal.sql` agrega `deleted_at` nullable sin modificar filas ni referencias. La versión posterior a V1001 permite actualizar bases locales con seeds ya aplicados; también pertenece a las migraciones de producción.
- Listados, categorías, edición/restauración, booking público, availability y selección/asociaciones nuevas excluyen eliminados. Lookup histórico conserva la fila para turnos y cancelaciones existentes.
- Bloqueo de fila en transacciones de editar/desactivar/eliminar evita restauración concurrente del servicio eliminado.
- Pruebas de API/persistencia para activos, eliminación, invisibilidad, restauración prohibida y aislamiento; prueba PostgreSQL de migración sobre seed existente y conservación de referencia de un turno.
- Reiniciar API local para aplicar Flyway antes de probar Eliminar. No se eliminan físicamente servicios ni se cambian datos históricos.
- Verificación final mediante `make test`: 443 aprobados sin errores ni omisiones, incluyendo migraciones PostgreSQL y el caso con un turno asociado. Frontend: 64 unitarios, 81 E2E afectados, lint, TypeScript y build correctos.

- PR backend [#76](https://github.com/Candela-98/Turnero-api/pull/76) creado el 2026-10-16 sobre la rama de #75; frontend relacionado [#11](https://github.com/Candela-98/Turnero-frontend/pull/11), sobre #10. Ambos abiertos para revisión del usuario; sin merge. Tras integrar TURN-96, cambiar bases a `develop`/`dev` e integrar TURN-98 backend antes que frontend.
- Actualización del PR #75 (2026-10-16): incorporado `develop` y resuelto el conflicto de Flyway conservando cobertura de limpieza de sesiones y reparación de secuencias. Ajustadas versiones esperadas para V1001. Suite completa mediante `make test`: 477 aprobados, 0 fallos, 0 omisiones. Pendiente de merge por el usuario.

- TURN-96 backend integrado mediante #75. PR #76 actualizado a base `develop` tras ese merge; incorporada la cobertura vigente de Flyway y ajustadas versiones esperadas para V1002. TURN-98 continúa pendiente de revisión e integración.
- Validación de #76 contra `develop` actualizado: `make test`, 480 aprobados sin fallos ni omisiones. Conflicto documental resuelto conservando ambos registros y cobertura de V3/V1001/V1002 reconciliada.

## TURN-77 — Listado real de profesionales

- Implementación local: rama `feature/TURN-77-staff-list`, basada en TURN-98. GET `/api/v1/staff-members` pasa de array a `{ data, page }`; búsqueda parcial por nombre/rol/especialidad, estado, paginación y orden estable por nombre e ID, siempre en el negocio autenticado. Validación central de orden y límites; sin migraciones. Consulta interna anterior conservada.
- Contrato y OpenAPI actualizados; cobertura de filtros combinados, campos opcionales nulos, duplicados, aislamiento, vacíos y parámetros inválidos. Validación automatizada: ver resultados finales en la guía frontend `docs/qa-turn-77.md`.
- QA manual aprobada por el usuario; paginación cubierta por automatización por disponer de menos de 20 profesionales. Integración: pendiente. PR backend separado autorizado; integrar antes del frontend por el cambio de formato HTTP. Ajustar base cuando se integren las dependencias TURN-98.
- Verificación final backend: suite completa aprobada, 484 tests sin fallos ni omisiones, incluyendo PostgreSQL/Testcontainers en Colima. QA manual aprobada; integración pendiente.

- PRs TURN-77 creados: [backend #77](https://github.com/Candela-98/Turnero-api/pull/77) sobre TURN-98 y [frontend #12](https://github.com/Candela-98/Turnero-frontend/pull/12) sobre TURN-98. Sin revisión de CI ni merge por instrucción del usuario. Ajustar bases al integrar dependencias; backend primero.

## TURN-99 — Prerrequisito para crear/editar profesionales

- Implementación local en `feature/TURN-99-staff-form`, sobre TURN-77: validación de rol alineada con columna de 80 caracteres en POST/PATCH, sin migraciones. Contrato y OpenAPI actualizados.
- Alta transaccional junto con horarios; bloqueo pesimista del usuario antes de comprobar vinculación. Usuario fuera del negocio devuelve 404; duplicado, incluso inactivo, devuelve `409 STAFF_USER_ALREADY_LINKED`. El controller responde con la entidad persistida.
- Suite backend completa: 492 aprobadas, sin fallos ni omisiones. Cobertura de límites 80/81, conflicto HTTP y PostgreSQL real: copia semanal, altas con/sin usuario, aislamiento, duplicados, concurrencia y rollback.
- QA manual aprobada por el usuario el 2026-10-17; guía en frontend `docs/pruebas-locales-turn-99.md`. PR backend previo separado del frontend, tras aceptación del usuario; integrar antes de frontend. Bases dependientes de TURN-77. Sin revisión de CI ni merge automático.

- QA de vinculación: nuevo GET `/api/v1/staff-members/me`, limitado al usuario de sesión y negocio; devuelve `staff_member` o null, incluidos inactivos. Permite bloquear preventivamente una segunda vinculación sin recorrer páginas ni depender de filtros. Sin migraciones; requiere reiniciar API. Pruebas de sesión, negocio y consulta sin vínculo añadidas.

- Verificación de la consulta de vinculación: suite backend completa 495 aprobadas, sin fallos ni omisiones; incluye aislamiento por sesión/negocio, usuario sin vínculo, profesional inactivo y rechazo sin sesión.

- PRs TURN-99 creados el 2026-10-17: [backend #78](https://github.com/Candela-98/Turnero-api/pull/78) y [frontend #13](https://github.com/Candela-98/Turnero-frontend/pull/13), ambos sobre sus ramas de TURN-77. QA manual aprobada. Sin revisión de CI ni merge; ajustar bases al integrar dependencias y entregar backend primero.

## TURN-78 — Servicios por profesional (2026-10-17)

- Implementación local en `feature/TURN-78-staff-services`, basada en TURN-99. GET mantiene array de DTOs de servicios y PUT mantiene 204; corregidos contrato y OpenAPI. Lista vacía quita todas las asociaciones, IDs inválidos devuelven 400, duplicados se normalizan, recursos ajenos/inexistentes/eliminados devuelven 404. Inactivos existentes se conservan o quitan; nuevas asociaciones inactivas devuelven 409.
- Reemplazo transaccional: bloqueo del profesional, servicios solicitados bloqueados por ID, validación previa y actualización por diferencia. Conserva filas sin cambios, serializa escrituras concurrentes y revierte todas las modificaciones ante errores. Sin migraciones ni cambios a turnos futuros/históricos.
- QA automática: suite completa final de Gradle: 510 tests aprobados, sin errores ni omisiones; rollback comprobado con DELETE efectivamente enviado a PostgreSQL. Cobertura de controller, dominio y PostgreSQL: varios/uno/ninguno, normalización, validación, aislamiento, inactivos, eliminados, filas retenidas, concurrencia, rollback y turnos intactos.
- QA manual aprobada por el usuario el 2026-10-17; guía frontend `docs/pruebas-locales-turn-78.md`. Creación de PR backend separado autorizada, con base TURN-99 mientras no esté integrado; integración pendiente y backend antes que frontend. Sin revisión de CI ni merge automático.
- Decisión de alcance: formularios de turnos y agenda siguen demo. Su criterio de refresco de servicios queda diferido hasta integración real; el frontend invalida solo consultas reales de asociaciones/profesionales/servicios afectados.

- Mejora de visibilidad TURN-78: GET de profesionales incluye `service_count`, agregado para los IDs de la página en una única consulta limitada al negocio. Cuenta inactivos asignados y excluye eliminados/ajenos; profesionales sin asignaciones devuelven 0. No introduce consultas por fila ni carga del catálogo. DTO/contrato/OpenAPI actualizados; detalle y escrituras mantienen su formato. Suite backend actualizada: 513 pruebas aprobadas, sin fallos ni omisiones, con cobertura PostgreSQL de conteo/aislamiento y pruebas del contrato HTTP.

- PRs TURN-78 creados el 2026-10-17: [backend #79](https://github.com/Candela-98/Turnero-api/pull/79) y [frontend #14](https://github.com/Candela-98/Turnero-frontend/pull/14), ambos sobre `feature/TURN-99-staff-form`. QA manual aprobada. Sin revisión de CI ni merge; ajustar bases al integrar TURN-99 y entregar backend primero.

- Integración de dependencias autorizada por el usuario el 2026-10-17 sin esperar approvals: TURN-98 [#76](https://github.com/Candela-98/Turnero-api/pull/76), TURN-77 [#77](https://github.com/Candela-98/Turnero-api/pull/77) y TURN-99 [#78](https://github.com/Candela-98/Turnero-api/pull/78) mergeados, en ese orden, a `develop` con commits de merge. TURN-78 [#79](https://github.com/Candela-98/Turnero-api/pull/79) queda abierto sobre `develop`, sin conflictos; frontend relacionado [#14](https://github.com/Candela-98/Turnero-frontend/pull/14) sobre `dev`. Sin consultar CI. Integrar TURN-78 backend antes del frontend.
