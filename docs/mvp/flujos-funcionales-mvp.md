# Turnero API - Flujos Funcionales MVP

## Proposito

Este documento define los flujos funcionales MVP antes de disenar contratos HTTP o codear backend.

Fuentes:

- Pantallas aprobadas en Stitch.
- `schema-db-mvp.md`.
- `proximos-pasos-mvp.md`.
- Backend actual Spring Boot, que ya cubre CRUD base de clientes, servicios, profesionales y turnos.

## Principios

- El MVP apunta a tiendas chicas.
- El owner entra con Google.
- El negocio se provisiona de forma controlada, no con registro publico self-service.
- El cliente final puede reservar sin login.
- La agenda diaria es el centro operativo.
- Todo turno persistido debe tener profesional asignado.
- `business_id` debe aislar datos entre negocios.
- Los estados visuales de Stitch deben mapearse a estados controlados del backend.

## Actores

### Owner

Usuario autenticado con Google y rol `OWNER`.

Puede:

- Administrar configuracion del negocio.
- Crear profesionales.
- Crear servicios.
- Asociar servicios a profesionales.
- Crear, editar, cancelar y confirmar turnos.
- Ver agenda, clientes, servicios, profesionales y dashboard.

Puede tambien atender turnos si tiene un `staff_member` asociado.

### Staff member

Persona que atiende turnos.

Puede existir sin login. En el MVP, lo importante es que pueda:

- Aparecer en agenda.
- Tener horarios.
- Tener servicios asignados.
- Recibir appointments.

### Customer

Cliente del negocio.

En MVP:

- No necesita login.
- Puede reservar desde booking publico.
- Puede cancelar desde link/token seguro.

Futuro:

- Puede vincularse a `users` con rol `CUSTOMER`.

## Flujo 1 - Login owner con Google

### Objetivo

Permitir que el owner entre al sistema usando Google.

### Precondiciones

- El usuario tiene una cuenta Google.
- El negocio puede estar ya asignado o pendiente de asignacion/provisioning.

### Flujo principal

1. Owner inicia login con Google.
2. Google devuelve una prueba de identidad al frontend, por ejemplo un `id_token` o authorization code segun el contrato elegido.
3. Frontend envia esa prueba al backend.
4. Backend valida la identidad contra Google, incluyendo firma, issuer, audience, expiracion, `sub` y `email_verified = true`.
5. Backend busca `users` por `auth_provider = GOOGLE` y `auth_subject`.
6. Si no existe, crea o deja preparado `users` segun el flujo de provisioning definido.
7. Si el usuario no tiene `business_id`, el backend rechaza el acceso admin hasta que se complete provisioning.
8. Si el usuario existe y tiene `business_id`, el backend crea una sesion propia de Turnero.
9. Backend genera un token aleatorio de sesion, guarda su hash en `user_sessions.session_token_hash` y define `expires_at`.
10. Backend devuelve el token plano al navegador en una cookie HTTP-only, Secure y SameSite.
11. Frontend entra al admin.
12. En cada request admin, backend valida la cookie contra `user_sessions` y autoriza usando `users.business_id` y `users.role`.

### Reglas

- Para MVP, el rol operativo inicial es `OWNER`.
- No hay login email/password propio.
- No hay registro publico self-service.
- Todo acceso admin debe tener usuario autenticado.
- El token de Google solo se usa para validar identidad.
- El backend solo puede crear o autenticar `users` desde Google si `email_verified = true`.
- Turnero no envia emails de verificacion en MVP; delega esa verificacion en Google.
- `auth_subject` sigue siendo el identificador externo principal; `email` no reemplaza a `sub`.
- Turnero no usa el token de Google como sesion interna.
- Turnero emite una sesion propia despues de validar Google.
- La DB guarda hash del token de sesion, no el token plano.
- El frontend no debe guardar tokens de sesion en `localStorage`.
- Logout revoca la sesion completando `user_sessions.revoked_at`.
- Sesiones vencidas o revocadas no autentican requests.
- `users.id` es el identificador interno estable; el resto del dominio no debe depender de identificadores de Google.
- Los campos `auth_provider` y `auth_subject` quedan genericos para permitir una migracion post-MVP a Auth0 u otra plataforma.

### Errores esperados

- Google token invalido.
- Email de Google no verificado.
- Usuario sin business asignado.
- Usuario sin permisos para acceder al admin.
- Sesion inexistente.
- Sesion vencida.
- Sesion revocada.

## Flujo 2 - Provisioning de negocio

### Objetivo

Crear una tienda nueva para un owner y dejarla en estado inicial usable.

### Decision MVP

El alta de negocio es controlada/interna. El owner se crea con Google y luego se le asigna un `business`.

### Flujo principal

1. Resolver o crear `users` desde identidad Google validada.
2. Crear `businesses`.
3. Asignar `business_id` al `user`.
4. Setear `users.role = OWNER`.
5. Setear `businesses.status = ACTIVE`.
6. Setear `businesses.onboarding_status = PENDING_SETUP`.
7. Crear `booking_settings` con defaults MVP.
8. Crear `business_hours` con defaults MVP.

### Defaults booking_settings

```text
public_booking_enabled: true
requires_customer_login: false
booking_window_days: 7
min_notice_hours: 3
cancellation_notice_hours: 3
slot_interval_minutes: 30
manual_confirmation_enabled: true
whatsapp_reminders_enabled: false
```

### Defaults business_hours

```text
MONDAY: closed
TUESDAY: 09:00-20:00
WEDNESDAY: 09:00-20:00
THURSDAY: 09:00-20:00
FRIDAY: 09:00-20:00
SATURDAY: 09:00-20:00
SUNDAY: closed
```

### Reglas

- No se crea `staff_members` automaticamente.
- Si el owner tambien atiende, se crea `staff_members` solo cuando lo pide/configura.
- Si se crea `staff_members` para el owner, se copia `business_hours` inicial a `staff_working_hours`.

### Errores esperados

- Owner no encontrado.
- Business slug duplicado.
- Datos minimos del negocio invalidos.

## Flujo 3 - Negocio listo para operar

### Objetivo

Definir cuando el negocio pasa de setup inicial a operativo.

### Criterio READY

`businesses.onboarding_status` puede pasar a `READY` cuando exista:

1. `businesses.status = ACTIVE`.
2. Owner asignado.
3. `booking_settings`.
4. `business_hours`.
5. Al menos un `staff_members` activo.
6. `staff_working_hours` para ese profesional.
7. Al menos un `service_offerings` activo.
8. Al menos una relacion `staff_service_offerings`.

### Reglas

- Mientras falte algun punto, el negocio queda en `PENDING_SETUP`.
- La UI debe apoyarse en empty states accionables.
- No se debe permitir publicar una experiencia de booking util sin servicio y profesional asignable.

### Empty states vinculados

- Sin servicios: `Agrega tu primer servicio`.
- Sin profesionales: `Agrega tu primer profesional`.
- Sin turnos: `No hay turnos para este dia`.
- Booking sin horarios: `No hay horarios disponibles`.
- Booking sin profesionales: `No hay profesionales disponibles`.

## Flujo 4 - Configurar negocio

### Objetivo

Permitir editar datos base, horarios y reglas de reserva.

### Datos editables

Business:

- Nombre.
- Rubro (`industry`).
- Telefono.
- Email.
- Direccion.
- Timezone.

Business hours:

- Dia de semana.
- Apertura.
- Cierre.
- Cerrado/no cerrado.

Booking settings:

- Anticipacion minima.
- Ventana de reserva.
- Intervalo de agenda.
- Cancelacion permitida hasta.
- Confirmacion manual.
- Reservas sin login.
- Recordatorios WhatsApp queda post-MVP.

Availability exceptions:

- Fecha cerrada.
- Horario especial.
- Bloqueo de franja.

### Reglas

- `requires_customer_login` debe quedar `false` en MVP.
- `slot_interval_minutes` controla generacion de slots.
- `manual_confirmation_enabled` define si booking entra `PENDING` o `CONFIRMED`.
- Excepciones pueden aplicar al negocio completo o a un profesional.

### Errores esperados

- Horario de apertura mayor o igual a cierre.
- Intervalo de agenda invalido.
- Ventana de reserva invalida.
- Anticipacion minima invalida.
- Excepcion fuera de rango o con horario invalido.

## Flujo 5 - Crear profesional

### Objetivo

Crear una persona que atiende turnos.

### Flujo principal

1. Owner completa datos del profesional.
2. Backend crea `staff_members` con `business_id`.
3. Si el profesional es el owner, se vincula `staff_members.user_id` al `users.id`.
4. Backend crea `staff_working_hours` inicial copiando `business_hours`.
5. Profesional queda `ACTIVE`.

### Datos principales

- Nombre.
- `role_label`, por ejemplo `Barbero`.
- Especialidad.
- Avatar opcional.
- `user_id` opcional.

### Reglas

- Un `user_id` solo puede estar vinculado a un `staff_member`.
- El profesional debe pertenecer al mismo `business_id` del owner.
- Editar horarios especificos por profesional queda post-MVP en UI, pero la tabla existe desde el principio.

### Errores esperados

- Nombre obligatorio.
- `user_id` ya vinculado a otro profesional.
- Usuario de otro negocio.

## Flujo 6 - Crear servicio

### Objetivo

Crear un servicio disponible para booking y agenda.

### Flujo principal

1. Owner crea `service_offerings`.
2. Owner asigna el servicio a uno o mas profesionales.
3. Backend crea relaciones `staff_service_offerings`.
4. Servicio queda `ACTIVE`.

### Datos principales

- Nombre.
- Categoria.
- Duracion en minutos.
- Precio en `price_cents`.
- Status.

### Reglas

- `price_cents` se guarda como entero.
- `duration_minutes` debe ser mayor a cero.
- Para que booking pueda ofrecer el servicio, debe tener al menos un profesional activo asociado.
- Desactivar servicio no debe borrar historial.

### Errores esperados

- Precio invalido.
- Duracion invalida.
- Categoria vacia si se decide obligatoria.
- Profesional inexistente o de otro negocio.

## Flujo 7 - Asociar servicios a profesional

### Objetivo

Definir que profesional puede atender que servicio.

### Flujo principal

1. Owner selecciona profesional.
2. Owner selecciona servicios habilitados.
3. Backend reemplaza o actualiza relaciones `staff_service_offerings`.

### Reglas

- Profesional y servicios deben pertenecer al mismo `business_id`.
- No se debe permitir crear appointment con servicio que el profesional no ofrece.
- Si se remueve una asociacion, no se modifica historial de appointments existentes.

### Errores esperados

- Profesional inexistente.
- Servicio inexistente.
- Relacion duplicada.
- Intento de asociar recursos de distintos negocios.

## Flujo 8 - Crear turno desde admin

### Objetivo

Crear un appointment manual desde agenda/admin.

### Flujo principal

1. Owner elige cliente o crea cliente rapido.
2. Owner elige servicio.
3. Owner elige profesional.
4. Owner elige fecha/hora.
5. Backend valida referencias.
6. Backend valida que el profesional ofrezca el servicio.
7. Backend calcula `duration_minutes`, `price_cents` y `ends_at`.
8. Backend valida disponibilidad y no solapamiento.
9. Backend crea `appointments` con `source = ADMIN`.

### Reglas

- `staff_member_id` es obligatorio.
- `price_cents` se copia como snapshot desde `service_offerings`.
- `duration_minutes` se copia desde `service_offerings`, salvo que se defina override futuro.
- Estados iniciales permitidos desde admin: `PENDING` o `CONFIRMED`.
- Appointments `PENDING` y `CONFIRMED` bloquean disponibilidad.

### Errores esperados

- Cliente inexistente.
- Servicio inexistente.
- Profesional inexistente.
- Profesional no ofrece el servicio.
- Horario ocupado.
- Profesional no disponible.
- Fecha fuera de horario.

## Flujo 9 - Editar turno desde admin

### Objetivo

Modificar datos de un appointment existente.

### Flujo principal

1. Owner abre turno.
2. Owner modifica cliente, servicio, profesional, horario, estado o notas.
3. Backend valida referencias.
4. Backend recalcula `ends_at`, `duration_minutes` y `price_cents` si cambia servicio.
5. Backend valida solapamiento excluyendo el mismo appointment.
6. Backend guarda cambios.

### Reglas

- `created_at` no cambia.
- `updated_at` se actualiza.
- Si cambia servicio, el nuevo `price_cents` debe reflejar el precio actual del servicio.
- Cancelar turno usa `status = CANCELLED` y puede guardar `cancellation_reason`.

### Errores esperados

- Appointment inexistente.
- Horario ocupado.
- Profesional no disponible.
- Servicio no ofrecido por profesional.

## Flujo 10 - Confirmar turno pendiente

### Objetivo

Permitir confirmar un turno `PENDING`.

### Flujo principal

1. Owner selecciona accion `Confirmar`.
2. Backend valida que el appointment exista.
3. Backend valida que status actual sea `PENDING`.
4. Backend cambia status a `CONFIRMED`.

### Reglas

- Solo `PENDING` puede confirmarse.
- Confirmar no debe cambiar precio, servicio, cliente ni profesional.
- Si el appointment fue cancelado, no puede confirmarse.

### Errores esperados

- Appointment inexistente.
- Estado invalido para confirmar.

## Flujo 11 - Booking publico

### Objetivo

Permitir que un cliente reserve sin login.

### Flujo principal

1. Cliente abre booking publico del negocio.
2. Backend expone servicios activos con profesionales disponibles.
3. Cliente elige servicio.
4. Cliente elige profesional especifico o `Cualquiera disponible`.
5. Cliente elige dia y horario.
6. Cliente completa nombre, telefono y email.
7. Backend busca o crea `customers`.
8. Backend valida reglas de reserva.
9. Backend valida disponibilidad real.
10. Si eligio `Cualquiera disponible`, backend asigna un profesional disponible.
11. Backend crea `appointments` con `source = PUBLIC_BOOKING`.
12. Backend define status:
    - `PENDING` si `manual_confirmation_enabled = true`.
    - `CONFIRMED` si `manual_confirmation_enabled = false`.
13. Backend genera `appointment_public_tokens` de tipo `CANCEL`.
14. Frontend muestra pantalla de confirmacion.

### Reglas

- Cliente no necesita login.
- `requires_customer_login` debe ser `false` en MVP.
- No se guarda appointment sin `staff_member_id`.
- El slot seleccionado debe seguir disponible al confirmar.
- Booking debe respetar:
  - `booking_window_days`.
  - `min_notice_hours`.
  - `slot_interval_minutes`.
  - `business_hours`.
  - `staff_working_hours`.
  - `availability_exceptions`.
  - appointments existentes.

### Errores esperados

- Negocio no existe.
- Booking publico deshabilitado.
- Servicio inactivo.
- No hay profesionales disponibles.
- No hay horarios disponibles.
- Horario tomado mientras reservaba.
- Datos del cliente invalidos.

## Flujo 12 - Cancelacion publica con token

### Objetivo

Permitir cancelar una reserva sin login y sin exponer acciones por `appointment_id`.

### Flujo principal

1. Cliente abre link de cancelacion con token.
2. Backend hashea token recibido.
3. Backend busca `appointment_public_tokens.token_hash`.
4. Backend valida:
   - existe.
   - `type = CANCEL`.
   - `used_at` es null.
   - `expires_at` no vencio.
5. Backend obtiene appointment.
6. Backend valida `cancellation_notice_hours`.
7. Backend cambia appointment a `CANCELLED`.
8. Backend guarda `cancellation_reason` si se informa.
9. Backend completa `used_at`.

### Reglas

- No usar `appointment_id` como autorizacion publica.
- No guardar token plano.
- El token debe ser largo, aleatorio y dificil de adivinar.
- No se puede cancelar un appointment ya cancelado.
- No se puede cancelar fuera de la ventana permitida.

### Errores esperados

- Token invalido.
- Token expirado.
- Token ya usado.
- Appointment inexistente.
- Cancelacion fuera de plazo.
- Appointment ya cancelado.

## Flujo 13 - Estados vacios de primer uso

### Objetivo

Guiar al owner cuando el negocio todavia no tiene datos suficientes.

### Casos

#### Agenda sin turnos

Acciones:

- Crear turno.
- Cambiar fecha.

#### Clientes sin clientes

Accion:

- Nuevo cliente.

#### Servicios sin servicios

Accion:

- Nuevo servicio.

#### Profesionales sin profesionales

Accion:

- Nuevo profesional.

#### Booking sin horarios

Acciones:

- Cambiar fecha.
- Cualquiera disponible.

#### Booking sin profesionales disponibles

Acciones:

- Cambiar fecha.
- Cualquiera disponible.

## Reglas transversales

### Business scope

Todo recurso admin debe filtrarse por `business_id`.

No se debe permitir:

- Crear appointment usando cliente de otro negocio.
- Asociar profesional y servicio de negocios distintos.
- Leer o modificar recursos de otro negocio.

### Auditoria

Backend controla:

- `created_at`.
- `updated_at`.

El cliente API no debe enviar ni sobrescribir auditoria.

### Estados que bloquean disponibilidad

Bloquean:

- `PENDING`.
- `CONFIRMED`.

No bloquean:

- `CANCELLED`.
- `COMPLETED`.
- `NO_SHOW`.

### Precio y duracion

Al crear appointment:

- `duration_minutes` se copia desde el servicio.
- `price_cents` se copia desde el servicio.

Esto preserva historial aunque cambie el servicio despues.
