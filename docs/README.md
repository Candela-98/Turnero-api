# Documentacion Turnero API

## Proposito

Este archivo es el indice de documentacion backend. No es fuente tecnica completa: apunta a los documentos que si lo son.

## Recorridos de lectura

### Para entender el MVP

1. `mvp/flujos-funcionales-mvp.md`
2. `mvp/schema-db-mvp.md`
3. `mvp/auth/google-mvp.md`
4. `mvp/api-contracts-mvp.md`

### Para retomar implementacion

1. `mvp/tracking-implementacion-mvp.md`
2. `mvp/proximos-pasos-mvp.md`
3. `mvp/plan-migracion-backend-mvp.md`
4. `mvp/schema-db-mvp.md`
5. `mvp/flujos-funcionales-mvp.md`
6. `mvp/auth/google-mvp.md`
7. `mvp/api-contracts-mvp.md`
8. `referencias/arquitectura-backend.md`
9. `referencias/buenas-practicas-java-spring.md`

`referencias/guia-historias-tecnicas.md` se conserva como referencia para escribir historias tecnicas, pero no representa el backlog vigente.
`referencias/arquitectura-backend.md` se conserva como guia practica de arquitectura backend.
`referencias/buenas-practicas-java-spring.md` se conserva como guia practica para implementar y revisar cambios Java/Spring Boot.
El backlog activo vive en Jira bajo la epica `TURN-1`; `backlog-jira-mvp.md` es tracking local y no se versiona.

## Proyecto

- Backend: Spring Boot 3.5, Java 21, Gradle, JPA, PostgreSQL/Flyway en desarrollo y H2 para tests rapidos; Testcontainers valida migraciones sobre PostgreSQL.
- Frontend de referencia: `../turnero-frontend`.
- Fuente visual/funcional del MVP: pantallas aprobadas en Stitch.
- Caso inicial: tiendas chicas, un negocio por cuenta, owner/admin inicial, booking publico sin login y agenda diaria como centro operativo.
- Deploy previsto: AWS.

## Fuentes de Verdad

### `mvp/schema-db-mvp.md`

Fuente para modelo de datos:

- Tablas.
- Campos.
- Relaciones.
- Enums/status controlados.
- Decisiones de persistencia.

### `mvp/flujos-funcionales-mvp.md`

Fuente para comportamiento funcional:

- Login owner.
- Provisioning de negocio.
- Setup inicial y criterio READY.
- Configuracion.
- Profesionales.
- Servicios.
- Turnos admin.
- Booking publico.
- Cancelacion publica.
- Empty states.

### `mvp/auth/google-mvp.md`

Fuente para implementacion de auth:

- Google como proveedor de identidad.
- `auth_provider` y `auth_subject`.
- Validacion de `email_verified`.
- Sesion opaca propia de Turnero.
- Cookie HTTP-only, Secure y SameSite.
- Logout y validacion de requests admin.
- Camino post-MVP a Auth0 u otra plataforma.

### `mvp/api-contracts-mvp.md`

Fuente para contratos HTTP MVP:

- Convencion base `/api/v1`.
- Endpoints admin autenticados y endpoints publicos.
- DTOs principales de request/response.
- Filtros, paginacion y formato de errores.
- Reglas de scoping por `business_id`.
- Estados HTTP y notas de seguridad/autorizacion por endpoint.

### `mvp/proximos-pasos-mvp.md`

Guia de prioridades estrategicas:

- Orden general de trabajo.
- Riesgos transversales.
- Referencias al documento fuente de cada decision.

No contiene el estado detallado de implementacion ni el alcance de cada PR.

### `mvp/tracking-implementacion-mvp.md`

Fuente para estado real de implementacion:

- PRs del plan ya mergeados en `develop`.
- Proximo PR recomendado.
- Alcances que no deben asumirse como completos todavia.

### `mvp/plan-migracion-backend-mvp.md`

Fuente para migracion incremental backend:

- Orden de subtareas/PRs pensado para una desarrolladora junior inicial.
- Estrategia PostgreSQL, Docker Compose, Makefile local, `.env` y perfiles.
- Flyway.
- Migracion de entidades y endpoints actuales hacia el schema y contratos MVP.
- Limites de alcance por PR: objetivo unico, cambios revisables y hasta 2 endpoints por PR feature.
- Scoping por `business_id`, auth Google, public booking y robustez transversal.

## Referencias

### `referencias/guia-historias-tecnicas.md`

Referencia para escribir historias tecnicas, subtareas, criterios de aceptacion y Definition of Done.

### `referencias/arquitectura-backend.md`

Guia practica de arquitectura backend:

- Capas, responsabilidades y flujo de request.
- Contexto de negocio, auth, scoping, persistencia y contratos.
- Errores, logging, observabilidad, testing y uso con Codex.

### `referencias/buenas-practicas-java-spring.md`

Guia practica para implementar y revisar cambios Java/Spring Boot:

- SOLID, DRY, KISS y Clean Code.
- Uso de interfaces, capas, DTOs, services, repositories y mappers.
- JPA, Flyway, PostgreSQL, contratos HTTP, errores, logging y seguridad.
- Testing, coverage esperado y trabajo con Codex.

## Decisiones de Alto Nivel

- No hay email/password propio para admin en MVP.
- No hay registro publico self-service de negocios en MVP.
- El alta de negocio es controlada/interna.
- El cliente final puede reservar sin login.
- Todo appointment persistido debe tener `staff_member_id`.
- `business_id` aisla datos entre negocios en MVP.
- Google valida identidad, pero Turnero emite su propia sesion.
- Las tareas activas viven en Jira; los docs locales explican contexto tecnico y decisiones.

## Regla de Trabajo

Para iniciar o retomar una subtarea backend, seguir este orden:

1. Consultar el estado en `tracking-implementacion-mvp.md` y en Jira.
2. Leer el alcance exacto en `plan-migracion-backend-mvp.md`.
3. Leer solo las fuentes de comportamiento, contrato, persistencia o auth que afecte la subtarea.
4. Implementar incrementalmente con tests.
5. Actualizar el tracking al mergearse el cambio; no usar el plan como registro de avance.
