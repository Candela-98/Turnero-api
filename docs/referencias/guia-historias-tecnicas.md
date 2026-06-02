# Guia para historias tecnicas

> Nota: Este documento no representa el backlog vigente de Turnero.
> Se conserva como referencia para escribir historias tecnicas, subtareas,
> criterios de aceptacion y Definition of Done con buen nivel de detalle.
> Las tareas activas viven en Jira.

## Contexto

Turnero API es un backend Spring Boot para gestionar turnos, clientes, servicios y profesionales. El proyecto esta pensado como entorno de aprendizaje real, por lo que los cambios deben hacerse de forma incremental, con PRs chicos, revisables y testeados.

La API ya cuenta con una base de manejo global de errores:

- `src/main/java/com/turnero/api/exception/ErrorResponse.java`
- `src/main/java/com/turnero/api/exception/GlobalExceptionHandler.java`
- `src/main/java/com/turnero/api/exception/ResourceNotFoundException.java`
- `src/test/java/com/turnero/api/exception/GlobalExceptionHandlerTest.java`

El objetivo de esta historia es mejorar gradualmente la robustez del backend para acercarlo a un entorno productivo sin mezclar demasiados conceptos en un mismo PR.

## Criterio general de trabajo

- Cada subtarea representa idealmente un PR.
- Cada PR debe incluir codigo y tests cuando aplique.
- Priorizar cambios concretos y faciles de revisar.
- Mantener el estilo actual del proyecto salvo que el cambio requiera ajustar el patron.
- No introducir refactors grandes no relacionados con la subtarea.
- Estabilizar primero contratos, validaciones y reglas de negocio; documentar con Swagger despues de que esos contratos esten mas firmes.
- Dejar Flyway/versionado de esquema para una etapa posterior, cuando el modelo de datos base este mas estable.
- Separar cambios de API/modelo de cambios futuros de esquema versionado. Las restricciones de base de datos deben reflejarse en Flyway cuando existan migraciones.

## Jira

Historia creada en Jira:

- `TURN-19`: Mejoras de robustez y calidad productiva de la API
- Epic padre: `TURN-1` - MVP Turnero
- Asignada a: Candela Leguizamon

Subtareas creadas:

- `TURN-20`: Cerrar manejo consistente de errores
- `TURN-21`: Estabilizar contratos de entrada/salida y validaciones
- `TURN-22`: Auditar fechas desde backend
- `TURN-23`: Validar referencias en turnos
- `TURN-24`: Evitar solapamiento de turnos
- `TURN-25`: Mejorar observabilidad
- `TURN-26`: Documentar API con Swagger/OpenAPI

## Subtareas / PRs

### 1. Cerrar manejo consistente de errores

**Objetivo:** alinear la app al contrato de errores ya implementado y asegurar que endpoints reales respondan con formato consistente.

**Trabajo esperado:**

- Limpiar usos o mocks residuales de `ResponseStatusException` donde ya no representen el flujo real.
- Alinear tests para usar `ResourceNotFoundException` cuando simulan errores de dominio.
- Ampliar tests de endpoints reales para validar el JSON de error.
- Validar campos como `status`, `error`, `message` y `validations`.
- Revisar si `handleGeneric` debe devolver un mensaje generico en lugar de exponer `ex.getMessage()` al cliente.

Ejemplo esperado en tests:

```java
mockMvc.perform(get("/api/appointments/{id}", 999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Appointment not found with ID: 999"));

mockMvc.perform(post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Validation Error"))
        .andExpect(jsonPath("$.validations.customerId").exists());
```

**Codigo a revisar/tocar:**

- `src/main/java/com/turnero/api/exception/GlobalExceptionHandler.java`
- `src/main/java/com/turnero/api/exception/ErrorResponse.java`
- `src/main/java/com/turnero/api/exception/ResourceNotFoundException.java`
- `src/test/java/com/turnero/api/controller/ServiceOfferingControllerTest.java`
- `src/test/java/com/turnero/api/controller/StaffMemberControlTest.java`
- `src/test/java/com/turnero/api/controller/AppointmentControllerTest.java`
- `src/test/java/com/turnero/api/controller/CustomerControllerTest.java`
- `src/test/java/com/turnero/api/integration/AppointmentControllerIT.java`
- `src/test/java/com/turnero/api/integration/CustomerControllerIT.java`
- `src/test/java/com/turnero/api/integration/ServOfferingControllerIT.java`
- `src/test/java/com/turnero/api/integration/StaffMemberControllerIT.java`

**Criterio de aceptacion:**

- Los errores `404`, validaciones `400`, errores de tipo y errores inesperados tienen formato JSON consistente.
- Los tests de endpoints reales verifican estructura de error, no solo status HTTP.
- Los services no dependen de excepciones HTTP para representar errores de negocio.
- Los errores inesperados no exponen informacion interna innecesaria.

### 2. Estabilizar contratos de entrada/salida y validaciones

**Objetivo:** definir contratos publicos mas claros para la API, dejar de exponer entidades directamente y reforzar validaciones de entrada en el mismo PR.

**Trabajo esperado:**

- Introducir DTOs de salida en endpoints criticos.
- Evitar exponer entidades directamente cuando el contrato de API deba ser estable.
- Limpiar DTOs de entrada para que el cliente no envie campos internos o controlados por servidor.
- Revisar campos actuales como `customerId` en `CustomerRequestDto`, `staffMemberId` en `StaffMemberRequestDto`, `Id` en `ServOfferingRequestDto` y fechas de auditoria en requests.
- Agregar o ajustar validaciones faltantes en DTOs (`@NotBlank`, `@Email`, `@Positive`, `@PositiveOrZero`, `@Size`, etc. segun corresponda).
- Reemplazar `@NotNull` por validaciones mas especificas en strings cuando aplique.
- Revisar campos primitivos (`int`, `double`) que no pueden ser `null` y evitar validaciones redundantes como `@NotNull` sobre primitivos.
- Agregar restricciones de modelo con JPA donde sean claras (`@Column(nullable = false)` en campos criticos).
- Definir en codigo/tests cuales campos deben ser unicos a nivel negocio, por ejemplo `email` o `license`, sin mezclar todavia migraciones Flyway.
- Ajustar mappers para separar claramente:
  - request DTO -> entidad
  - entidad -> response DTO
- Ajustar tests de controllers e integracion para validar mensajes/campos de error.
- Actualizar tests de controllers e integracion para validar el contrato publico esperado.

**Codigo a revisar/tocar:**

- `src/main/java/com/turnero/api/controller/AppointmentController.java`
- `src/main/java/com/turnero/api/controller/CustomerController.java`
- `src/main/java/com/turnero/api/controller/ServOfferingController.java`
- `src/main/java/com/turnero/api/controller/StaffMemberController.java`
- `src/main/java/com/turnero/api/dto/AppointmentRequestDto.java`
- `src/main/java/com/turnero/api/dto/CustomerRequestDto.java`
- `src/main/java/com/turnero/api/dto/ServOfferingRequestDto.java`
- `src/main/java/com/turnero/api/dto/StaffMemberRequestDto.java`
- `src/main/java/com/turnero/api/dto/`
- `src/main/java/com/turnero/api/mapper/`
- `src/main/java/com/turnero/api/model/Appointment.java`
- `src/main/java/com/turnero/api/model/Customer.java`
- `src/main/java/com/turnero/api/model/ServiceOffering.java`
- `src/main/java/com/turnero/api/model/StaffMember.java`
- `src/test/java/com/turnero/api/controller/`
- `src/test/java/com/turnero/api/integration/`

**Criterio de aceptacion:**

- Los endpoints criticos devuelven DTOs de salida en lugar de depender directamente del modelo interno.
- Los request DTOs no aceptan IDs internos ni fechas de auditoria salvo que el caso de uso lo justifique explicitamente.
- Los tests validan el contrato de entrada/salida esperado.
- La API rechaza requests invalidos temprano con errores claros y consistentes.
- Los DTOs no usan validaciones debiles cuando existe una validacion mas precisa.
- Las entidades expresan restricciones basicas del modelo.
- Las reglas que requieran constraint real de base quedan identificadas para trasladarse a Flyway en una etapa posterior.

### 3. Auditar fechas desde backend

**Objetivo:** asegurar que las fechas de auditoria las controle el servidor.

**Trabajo esperado:**

- Gestionar `createdAt` y `updatedAt` desde backend.
- Corregir o decidir explicitamente el nombre actual `updateAt` en `Appointment`.
- Remover dependencia de fechas enviadas por request.
- Revisar `createdIn` en `Customer` y definir si debe ser audit field controlado por servidor.
- Mover la responsabilidad de setear fechas fuera de requests y evitar que el mapper sobrescriba fechas de auditoria en updates.
- Ajustar mappers, services y tests.

**Codigo a revisar/tocar:**

- `src/main/java/com/turnero/api/model/Appointment.java`
- `src/main/java/com/turnero/api/model/Customer.java`
- `src/main/java/com/turnero/api/dto/AppointmentRequestDto.java`
- `src/main/java/com/turnero/api/dto/CustomerRequestDto.java`
- `src/main/java/com/turnero/api/mapper/AppointmentMapper.java`
- `src/main/java/com/turnero/api/mapper/CustomerMapper.java`
- `src/main/java/com/turnero/api/service/AppointmentServiceImpl.java`
- `src/main/java/com/turnero/api/service/CustomerServiceImpl.java`
- `src/test/java/com/turnero/api/service/`
- `src/test/java/com/turnero/api/integration/`

**Criterio de aceptacion:**

- El cliente no puede definir ni sobrescribir fechas de auditoria.
- `createdAt` se setea al crear y `updatedAt` se actualiza al modificar.
- `createdAt` no cambia en updates.
- Los tests reflejan el nuevo contrato.

### 4. Validar referencias en turnos

**Objetivo:** evitar que se creen o editen turnos apuntando a entidades inexistentes.

**Trabajo esperado:**

- Validar existencia de `customerId`, `serviceId` y `staffMemberId` en create/update de turnos.
- Inyectar los repositories necesarios en `AppointmentServiceImpl` o extraer una validacion clara si el codigo empieza a crecer.
- Definir mensajes de error consistentes cuando una referencia no existe.
- Asegurar que no se persista el turno si falla cualquiera de las referencias.
- Cubrir casos invalidos con tests.

**Codigo a revisar/tocar:**

- `src/main/java/com/turnero/api/service/AppointmentServiceImpl.java`
- `src/main/java/com/turnero/api/service/AppointmentService.java`
- `src/main/java/com/turnero/api/repository/CustomerRepository.java`
- `src/main/java/com/turnero/api/repository/ServOfferingRepository.java`
- `src/main/java/com/turnero/api/repository/StaffMemberRepository.java`
- `src/test/java/com/turnero/api/service/AppointmentServiceImplTest.java`
- `src/test/java/com/turnero/api/integration/AppointmentControllerIT.java`
- `src/test/java/com/turnero/api/controller/AppointmentControllerTest.java`

**Criterio de aceptacion:**

- No se persisten turnos con `customerId`, `serviceId` o `staffMemberId` inexistentes.
- La API responde error consistente cuando una referencia no existe.
- Los tests cubren create y update para al menos una referencia invalida, y preferentemente las tres.

### 5. Evitar solapamiento de turnos

**Objetivo:** impedir que un profesional tenga turnos superpuestos.

**Trabajo esperado:**

- Definir regla de solapamiento por profesional.
- Decidir si la duracion del turno viene de `AppointmentRequestDto.durationMinutes` o de `ServiceOffering.durationMinutes`.
- Validar conflicto horario en create/update.
- Agregar consultas necesarias al repository.
- Cubrir casos con tests.

**Codigo a revisar/tocar:**

- `src/main/java/com/turnero/api/model/Appointment.java`
- `src/main/java/com/turnero/api/repository/AppointmentRepository.java`
- `src/main/java/com/turnero/api/service/AppointmentServiceImpl.java`
- `src/main/java/com/turnero/api/service/AppointmentService.java`
- `src/test/java/com/turnero/api/service/AppointmentServiceImplTest.java`
- `src/test/java/com/turnero/api/integration/AppointmentControllerIT.java`

**Criterio de aceptacion:**

- Un profesional no puede tener dos turnos que se superpongan.
- La validacion aplica tanto al crear como al actualizar.
- La API responde error consistente ante conflicto.
- El update no se bloquea por el mismo turno que esta actualizando.

### 6. Mejorar observabilidad

**Objetivo:** reemplazar prints por logging util y consistente.

**Trabajo esperado:**

- Reemplazar `System.out.println` por logger.
- Usar SLF4J/Logback, preferentemente con `@Slf4j` porque el proyecto ya usa Lombok.
- Agregar logs utiles en operaciones relevantes.
- Agregar `log.error` en errores inesperados del `GlobalExceptionHandler`, si corresponde.
- Evitar logs ruidosos, duplicados o con datos sensibles.

**Codigo a revisar/tocar:**

- `src/main/java/com/turnero/api/service/AppointmentServiceImpl.java`
- `src/main/java/com/turnero/api/service/CustomerServiceImpl.java`
- `src/main/java/com/turnero/api/service/ServOfferingServiceImpl.java`
- `src/main/java/com/turnero/api/service/StaffMemberServiceImpl.java`
- Opcional: `src/main/java/com/turnero/api/exception/GlobalExceptionHandler.java`

**Criterio de aceptacion:**

- No quedan `System.out.println` en codigo productivo.
- Los logs aportan informacion util para troubleshooting.
- Los tests no dependen de logs ni prints.

### 7. Documentar API con Swagger/OpenAPI

**Objetivo:** exponer documentacion interactiva para facilitar consumo, pruebas y aprendizaje.

**Trabajo esperado:**

- Incorporar dependencia de Swagger/OpenAPI.
- Exponer documentacion interactiva de endpoints.
- Documentar requests, responses y errores principales.
- Usar los request/response DTOs ya estabilizados.
- Verificar que la app levante correctamente con la nueva dependencia.

**Codigo a revisar/tocar:**

- `build.gradle`
- `src/main/java/com/turnero/api/controller/`
- `src/main/java/com/turnero/api/dto/`
- `src/main/java/com/turnero/api/exception/ErrorResponse.java`
- `src/main/resources/application.properties`

**Criterio de aceptacion:**

- Swagger UI queda disponible al levantar la aplicacion.
- La documentacion muestra endpoints, request bodies, responses y errores principales.
- La documentacion refleja los contratos reales ya cubiertos por tests.

## Fuera de alcance por ahora

### Versionar esquema con Flyway

Esta tarea se posterga porque la base de datos final puede cambiar cuando se definan mejor los flujos reales de la app. Conviene pensar el modelo ahora, pero no fijarlo en migraciones todavia.

Antes de agregar Flyway conviene definir:

- Si la app soportara una sola barberia o multiples negocios/sucursales.
- Si `Appointment` guardara solo IDs o relaciones JPA reales.
- Si `durationMinutes` del turno viene del request, del servicio o queda como snapshot historico.
- Si el turno debe guardar snapshot de servicio, precio y duracion al momento de reservar.
- Nombres definitivos de tablas y columnas, por ejemplo `customers`, `staff_members`, `service_offerings`, `appointments`, `created_at`, `updated_at`.
- Si se usara borrado fisico o `active`/soft delete para clientes, servicios y profesionales.
- Que campos son unicos a nivel negocio, por ejemplo email, telefono o licencia.

Trabajo esperado cuando se retome:

- Incorporar Flyway.
- Crear migracion inicial.
- Reflejar en migraciones las restricciones de persistencia definidas previamente (`nullable`, `unique`, tipos, nombres de columnas/tablas).
- Ajustar configuracion runtime/test.
- Definir como conviven H2 de test y la base runtime.
- Decidir si runtime sigue usando H2 o si se prepara configuracion para MySQL/PostgreSQL.

**Codigo a revisar/tocar:**

- `build.gradle`
- `src/main/resources/application.properties`
- `src/test/resources/application-test.properties`
- `src/main/resources/db/migration/`

Criterio de aceptacion futuro:

- El esquema se crea y evoluciona mediante migraciones versionadas.
- Tests siguen corriendo con configuracion controlada.
- La app no depende de `ddl-auto=create-drop` fuera del perfil de test, salvo decision explicita documentada.

## Posibles historias posteriores

### Gestion de disponibilidad y ciclo de vida de turnos

Esta seria una buena historia posterior a robustez API, porque mueve el proyecto de CRUD simple a comportamiento real de turnero.

Posibles tareas:

- Definir modelo funcional de negocio: una barberia vs multiples negocios/sucursales.
- Modelar horarios laborales de profesionales.
- Modelar dias no disponibles, vacaciones, feriados o bloqueos manuales.
- Ampliar estados de turno: `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.
- Definir transiciones validas entre estados.
- Crear endpoints especificos para cancelar y reprogramar turnos.
- Agregar busquedas por fecha, profesional, cliente y estado.
- Evaluar soft delete para entidades maestras.
- Agregar snapshots historicos de servicio/precio/duracion en turnos.

## Criterios de aceptacion de la historia

- La API responde errores con formato consistente.
- La entrada de datos tiene validaciones claras y testeadas.
- Los contratos de entrada y salida son mas estables y testeados.
- Los turnos no permiten referencias invalidas ni solapamientos.
- Las fechas de auditoria las controla el backend.
- La app no depende de `System.out.println` para depuracion.
- La API cuenta con documentacion interactiva.
- La persistencia tiene restricciones basicas identificadas para reflejarse en futuras migraciones.

## Definition of Done sugerida

- El PR compila.
- `./gradlew test` pasa.
- Se agregan o actualizan tests para el cambio cuando aplique.
- No se mezclan cambios no relacionados.
- El PR describe brevemente que problema resuelve y como se probo.
