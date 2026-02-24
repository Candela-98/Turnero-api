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

## Funcionalidades principales

- Crear, modificar y cancelar turnos
- Gestión de clientes
- Gestión de servicios
- Gestión de profesionales
- Estados del turno (pendiente, confirmado, cancelado)
- Validaciones básicas
- Tests unitarios