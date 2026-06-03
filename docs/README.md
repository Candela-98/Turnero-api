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

1. `mvp/proximos-pasos-mvp.md`
2. `mvp/plan-migracion-backend-mvp.md`
3. `mvp/schema-db-mvp.md`
4. `mvp/flujos-funcionales-mvp.md`
5. `mvp/auth/google-mvp.md`
6. `mvp/api-contracts-mvp.md`
7. `referencias/arquitectura-backend.md`
8. `referencias/buenas-practicas-java-spring.md`

`referencias/guia-historias-tecnicas.md` se conserva como referencia para escribir historias tecnicas, pero no representa el backlog vigente.
`referencias/arquitectura-backend.md` se conserva como guia practica de arquitectura backend.
`referencias/buenas-practicas-java-spring.md` se conserva como guia practica para implementar y revisar cambios Java/Spring Boot.
El backlog activo vive en Jira bajo la epica `TURN-1`; `backlog-jira-mvp.md` es tracking local y no se versiona.

## Proyecto

- Backend: Spring Boot 3.5, Java 21, Gradle, JPA, H2 en desarrollo/tests.
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

Fuente para estado y roadmap:

- Que esta listo.
- Que falta.
- Brechas contra el backend actual.
- Orden recomendado antes de codear.

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

Antes de codear backend, mantener este orden:

1. Cerrar flujos funcionales.
2. Definir contratos HTTP.
3. Definir plan de migracion por PRs.
4. Definir migraciones DB.
5. Implementar incrementalmente con tests.
