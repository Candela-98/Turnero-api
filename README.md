# Turnero API 

API REST desarrollada en **Java con Spring Boot** para la gestión de turnos de una barbería.
Permite administrar turnos, clientes, servicios y profesionales, aplicando buenas prácticas de backend y arquitectura en capas.

---

## Tecnologías utilizadas

- Java
- Spring Boot
- Spring Web
- Spring Data JPA
- Gradle
- JUnit / Mockito (tests)
- H2 / MySQL (configurable)
- Git & GitHub

---

## Arquitectura

El proyecto sigue una **arquitectura en capas**, separando responsabilidades:

- **Controller** → expone los endpoints REST
- **Service** → lógica de negocio
- **Repository** → acceso a datos
- **Model / Entity** → entidades del dominio
- **Test** → pruebas unitarias

---

## Documentacion MVP

La documentacion tecnica y funcional vive en `docs/`:

- `docs/README.md` - indice de documentacion backend.
- `docs/mvp/proximos-pasos-mvp.md` - roadmap y brechas pendientes.
- `docs/mvp/schema-db-mvp.md` - esquema de base de datos objetivo.
- `docs/mvp/flujos-funcionales-mvp.md` - flujos funcionales del MVP.
- `docs/mvp/api-contracts-mvp.md` - contratos HTTP del MVP.
- `docs/mvp/plan-migracion-backend-mvp.md` - plan incremental de migracion backend.
- `docs/mvp/auth/google-mvp.md` - implementacion de Google Auth para el MVP.
- `docs/referencias/guia-historias-tecnicas.md` - referencia para escribir historias tecnicas.
- `docs/referencias/arquitectura-backend.md` - guia practica de arquitectura backend.
- `docs/referencias/buenas-practicas-java-spring.md` - guia de buenas practicas Java/Spring Boot.

---

## Funcionalidades principales

- Crear, modificar y cancelar turnos
- Gestión de clientes
- Gestión de servicios
- Gestión de profesionales
- Estados del turno (pendiente, confirmado, cancelado)
- Validaciones básicas
- Tests unitarios
