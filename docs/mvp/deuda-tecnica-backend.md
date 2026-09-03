# Deuda Tecnica Backend MVP

## Proposito

Registrar brechas tecnicas conocidas que no bloquean el avance actual, pero deben validarse y resolverse antes de cerrar la integracion MVP con frontend.

El caso de appointments documentado abajo es un ejemplo detectado durante una revision puntual. No representa una lista exhaustiva: antes de cerrar el MVP se debe revisar cada flujo backend + frontend contra sus contratos, reglas de negocio y datos persistidos para identificar inconsistencias equivalentes.

Este documento no reemplaza los contratos ni el backlog de Jira. Cada item que se decida implementar debe tener su ticket asociado.

## Auth - convergencia previa a TURN-69

Jira: `TURN-88` para contrato, autorizacion, cookie y logout; `TURN-89` para la proteccion de business-hours.

### Contrato HTTP

El contrato canónico de `api-contracts-mvp.md` y el código mergeado difieren en request de Google, body de login, estructura de `/auth/me`, cookie y autorización al crear sesión.

Antes de integrar el frontend:

- aceptar `id_token` en JSON;
- devolver el mismo contexto anidado `user`/`business` desde login y `/auth/me`;
- rechazar con `403` los roles sin acceso a la superficie admin MVP;
- parametrizar la cookie para usar `turnero_session` local y `__Host-turnero_session` en producción.

### Protección de business hours

`AdminAuthInterceptor` no incluye `/api/v1/business-hours/**`. El servicio depende de `AuthenticatedCurrentBusinessContext`, por lo que el endpoint debe incorporarse al interceptor y cubrirse con tests de sesión, rol y scoping antes de TURN-82.

### Logout idempotente

El logout actual exige una sesión todavía válida. El comportamiento objetivo es expirar la cookie y responder `204` aunque la cookie falte o la sesión esté vencida/revocada. Esto permite que el frontend siempre cierre su estado local sin convertir logout en un flujo de recuperación.

### Aprovisionamiento de desarrollo

El seed usa un `auth_subject` ficticio. Hace falta un procedimiento o herramienta repetible para asociar una cuenta Google de desarrollo a un OWNER local sin versionar datos personales ni habilitar registro público.

### Ciclo de vida de sesiones

Antes del hardening final:

- limpiar sesiones vencidas periódicamente;
- actualizar `last_seen_at` con throttling;
- definir límites o revocación global de sesiones por usuario;
- revisar CSRF si se abandona el BFF same-origin o cambia `SameSite`.

## Appointments - campos controlados por backend

Jira: `TURN-90` para lectura de agenda, `TURN-91` para invariantes de escritura y `TURN-92` para availability admin.

### Estado actual

`AppointmentMapper` copia desde `AppointmentRequestDto` los campos `duration_minutes`, `price_cents`, `ends_at` y `source`.

Esto deja valores de negocio controlados por el cliente y puede generar turnos inconsistentes con el servicio seleccionado.

### Comportamiento objetivo

Al crear un appointment administrativo, el backend debe usar `service_offering_id` y `starts_at` para calcular y persistir:

```text
duration_minutes = service_offering.duration_minutes
price_cents = service_offering.price_cents
ends_at = starts_at + duration_minutes
source = ADMIN
```

El request no debe poder definir esos valores. Si en el futuro existe un caso de negocio que permita una excepcion, debe tener un endpoint, permiso y validacion explicitos.

### Riesgos si se mantiene el estado actual

- Solapamientos calculados con una duracion incorrecta.
- Precio guardado distinto al del servicio reservado.
- `ends_at` incoherente con inicio y duracion.
- Origen del turno incorrecto para auditoria, reportes o reglas futuras.

### Validacion diferida

Durante la integracion final frontend + backend, validar:

- Crear un turno con un servicio y comprobar que backend define duracion, precio y fin.
- Rechazar o ignorar valores enviados para esos campos controlados.
- Confirmar que un turno creado desde admin queda con `source = ADMIN`.
- Confirmar que el control de solapamiento usa la duracion resuelta por backend.

Fuentes relacionadas:

- `api-contracts-mvp.md`, contratos admin de appointments.
- `plan-migracion-backend-mvp.md`, PR 5 de agenda y creacion de turnos.

## Revision transversal pendiente

El resumen operativo requerido por el dashboard se implementara en `TURN-93`; su contrato debe incorporarse a `api-contracts-mvp.md` antes de desarrollar el endpoint.

Durante las pruebas finales, revisar cada flujo MVP de punta a punta. Para cada uno, comprobar que:

- El request no permite modificar campos que deben controlar el backend.
- El backend aplica las reglas de negocio y persiste datos consistentes.
- La respuesta tiene los datos y el formato que necesita el frontend.
- Los estados, permisos y transiciones coinciden con el contrato.
- Las validaciones de seguridad, scoping y concurrencia se mantienen al escribir.

Registrar cada hallazgo en este documento y crear el ticket Jira correspondiente cuando se programe su resolucion.
