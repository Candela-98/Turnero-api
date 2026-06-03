# Turnero API - Contratos HTTP MVP

## Proposito

Este documento define los contratos HTTP del MVP antes de implementar controllers.

Fuentes:

- `schema-db-mvp.md` para tablas, campos y enums.
- `flujos-funcionales-mvp.md` para comportamiento.
- `auth/google-mvp.md` para login, sesiones y seguridad.

No reemplaza a esos documentos. Este archivo baja el MVP a URLs, DTOs, filtros, errores, estados HTTP y reglas de autorizacion.

## Decisiones API MVP

- Version base: `/api/v1`.
- Respuestas y requests usan JSON.
- Fechas y horas completas usan ISO-8601 con offset o UTC, por ejemplo `2026-04-28T15:30:00-03:00`.
- Horas sin fecha usan `HH:mm`, por ejemplo `09:00`.
- Fechas sin hora usan `YYYY-MM-DD`.
- Campos JSON usan `snake_case`.
- Query params que representan campos tambien usan `snake_case`.
- IDs se exponen como numeros.
- El backend controla `business_id`, `created_at` y `updated_at`; el cliente no los envia en endpoints admin comunes.
- Todo endpoint admin se scopea al `business_id` del usuario autenticado.
- Los endpoints publicos resuelven negocio por `business_slug` en la URL, que corresponde a `businesses.slug`.
- `staff_member_id` es obligatorio al persistir appointments. El valor especial `any` solo existe en requests publicas de disponibilidad/booking y el backend lo resuelve antes de guardar.
- `staff_working_hours` se expone en MVP porque impacta disponibilidad, aunque la UI pueda editarlo de forma limitada al principio.
- `availability_exceptions` queda fuera de CRUD principal de este contrato MVP salvo impacto en availability. Puede agregarse en un contrato posterior de configuracion avanzada.
- `notifications` queda post-MVP.

## Seguridad y scoping

### Admin autenticado

Endpoints bajo estas familias requieren cookie de sesion Turnero:

```text
/api/v1/auth/me
/api/v1/auth/logout
/api/v1/business
/api/v1/booking-settings
/api/v1/business-hours
/api/v1/staff-members
/api/v1/service-offerings
/api/v1/customers
/api/v1/appointments
/api/v1/availability
```

Reglas:

- Autenticacion por cookie HTTP-only `__Host-turnero_session`.
- El backend valida `user_sessions` y carga `users.business_id`.
- El request no puede elegir `business_id`.
- Todo read/write se filtra por `business_id` del usuario.
- Si un recurso existe pero pertenece a otro negocio, responder como `404 Not Found` o `403 Forbidden` segun convenga no revelar existencia. Regla recomendada MVP: `404 Not Found` para recursos por ID de otro negocio.
- Roles permitidos MVP: `OWNER`. `ADMIN`, `RECEPTIONIST` y `STAFF` quedan preparados para futuro.

### Publico

Endpoints publicos:

```text
/api/v1/public/businesses/{business_slug}/booking-profile
/api/v1/public/businesses/{business_slug}/services
/api/v1/public/businesses/{business_slug}/availability
/api/v1/public/businesses/{business_slug}/appointments
/api/v1/public/cancellations/{token}
```

Reglas:

- No requieren cookie de admin.
- No exponen datos internos, notas internas ni IDs innecesarios.
- Solo operan si `businesses.status = ACTIVE` y `booking_settings.public_booking_enabled = true`.
- `requires_customer_login` debe ser `false` en MVP.
- Cancelacion publica se autoriza por token aleatorio, no por `appointment_id`.

## Formato de errores

Formato base:

```json
{
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_ERROR",
  "message": "Hay campos invalidos",
  "details": [
    {
      "field": "starts_at",
      "message": "Debe estar dentro del horario de atencion"
    }
  ],
  "path": "/api/v1/appointments",
  "timestamp": "2026-04-28T18:30:00Z"
}
```

`details` es opcional. Para errores no asociados a campo se puede usar:

```json
{
  "code": "SLOT_UNAVAILABLE",
  "message": "El horario ya no esta disponible"
}
```

Codigos recomendados:

```text
VALIDATION_ERROR
UNAUTHENTICATED
FORBIDDEN
NOT_FOUND
CONFLICT
BUSINESS_NOT_READY
PUBLIC_BOOKING_DISABLED
SLOT_UNAVAILABLE
STAFF_SERVICE_MISMATCH
INVALID_STATE_TRANSITION
TOKEN_INVALID
TOKEN_EXPIRED
TOKEN_USED
```

Estados HTTP:

- `200 OK`: lectura, update o accion completada con body.
- `201 Created`: recurso creado.
- `204 No Content`: delete/logical delete sin body o logout exitoso.
- `400 Bad Request`: request mal formado o validacion de campos.
- `401 Unauthorized`: falta sesion, sesion vencida, token Google invalido.
- `403 Forbidden`: usuario autenticado sin permisos o sin negocio asignado.
- `404 Not Found`: recurso inexistente o fuera del scope.
- `409 Conflict`: conflicto de negocio, solapamiento, estado invalido.
- `422 Unprocessable Entity`: request bien formado pero viola reglas funcionales complejas. En MVP puede usarse `400` si se prefiere simplificar.

## Paginacion, filtros y orden

Listados admin con potencial crecimiento usan paginacion:

```text
?page=0&size=20&sort=created_at,desc
```

Respuesta:

```json
{
  "data": [],
  "page": {
    "number": 0,
    "size": 20,
    "total_elements": 57,
    "total_pages": 3
  }
}
```

Limites:

- `size` default: `20`.
- `size` maximo: `100`.

Listados chicos de configuracion pueden devolver arrays sin paginacion:

- Business hours.
- Staff working hours.
- Servicios asignados a un profesional.

## DTOs compartidos

### Convencion de nombres backend

Los nombres de esta seccion describen formas de respuesta del contrato. Al implementarlos en Java, usar DTOs especificos por recurso y profundidad:

```text
BusinessSummaryDTO
BusinessResponseDTO
BusinessUpdateRequestDTO
```

Criterio:

- `SummaryDTO`: version corta para listados o respuestas embebidas, por ejemplo el business dentro de `/auth/me`.
- `ResponseDTO`: respuesta completa del recurso en su endpoint propio, por ejemplo `GET /api/v1/business`.
- `CreateRequestDTO`: request de creacion cuando el recurso tenga endpoint `POST`.
- `UpdateRequestDTO`: request de edicion cuando el recurso tenga endpoint `PATCH` o `PUT`.

Si el backend existente usa otro casing para DTO, mantener la convencion local. La decision importante es separar DTOs completos de DTOs resumidos, no reutilizar una unica respuesta para todos los contextos.

Nota: los valores concretos de negocio en los ejemplos son ilustrativos del contrato. El seed dev debe alinearse con `../turnero-frontend/docs/referencias/datos-demo.md`, segun `plan-migracion-backend-mvp.md`.

### BusinessSummary

```json
{
  "id": 1,
  "name": "Barber Studio",
  "slug": "barber-studio",
  "industry": "Barberia premium",
  "email": "hola@barberstudio.demo",
  "phone": "+54 11 1234-5678",
  "address": "Av. Siempre Viva 123",
  "timezone": "America/Argentina/Buenos_Aires",
  "status": "ACTIVE",
  "onboarding_status": "PENDING_SETUP"
}
```

### CustomerSummary

```json
{
  "id": 10,
  "name": "Santiago Moreno",
  "email": "santiago.moreno@mail.demo",
  "phone_number": "+54 11 5555-5555",
  "status": "ACTIVE"
}
```

### StaffMemberSummary

```json
{
  "id": 1,
  "name": "Mateo Ruiz",
  "role_label": "Barbero",
  "specialty": "Cortes clasicos y barba",
  "avatar_url": "https://...",
  "status": "ACTIVE"
}
```

### ServiceOfferingSummary

```json
{
  "id": 20,
  "name": "Corte + barba",
  "category": "Combo",
  "duration_minutes": 90,
  "price_cents": 1650000,
  "status": "ACTIVE"
}
```

## Auth/session

### POST `/api/v1/auth/google`

Publico. Valida identidad Google y crea sesion propia Turnero.

Request:

```json
{
  "id_token": "eyJ..."
}
```

Response `200 OK`:

```json
{
  "user": {
    "id": 1,
    "name": "Mateo Ruiz",
    "email": "mateo@barberstudio.demo",
    "role": "OWNER",
    "avatar_url": "https://..."
  },
  "business": {
    "id": 1,
    "name": "Barber Studio",
    "slug": "barber-studio",
    "onboarding_status": "PENDING_SETUP"
  }
}
```

Headers:

```http
Set-Cookie: __Host-turnero_session=<token>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=604800
```

Errores:

- `401 UNAUTHENTICATED`: token Google invalido o `email_verified = false`.
- `403 FORBIDDEN`: usuario sin `business_id` o rol no permitido.

### GET `/api/v1/auth/me`

Admin autenticado. Devuelve sesion actual.

Response `200 OK`: mismo body que login.

Errores:

- `401 UNAUTHENTICATED`: sin cookie, sesion vencida o revocada.

### POST `/api/v1/auth/logout`

Admin autenticado. Revoca sesion actual.

Response `204 No Content`.

Headers:

```http
Set-Cookie: __Host-turnero_session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
```

## Business/configuracion

### GET `/api/v1/business`

Admin autenticado. Devuelve el negocio del usuario.

Response `200 OK`:

```json
{
  "id": 1,
  "name": "Barber Studio",
  "slug": "barber-studio",
  "industry": "Barberia premium",
  "email": "hola@barberstudio.demo",
  "phone": "+54 11 1234-5678",
  "address": "Av. Siempre Viva 123",
  "timezone": "America/Argentina/Buenos_Aires",
  "status": "ACTIVE",
  "onboarding_status": "PENDING_SETUP",
  "created_at": "2026-04-28T13:00:00Z",
  "updated_at": "2026-04-28T13:00:00Z"
}
```

### PATCH `/api/v1/business`

Admin autenticado. Edita datos base.

Request:

```json
{
  "name": "Barber Studio Palermo",
  "industry": "Barberia",
  "email": "hola@barberstudio.demo",
  "phone": "+54 11 1234-5678",
  "address": "Av. Siempre Viva 123",
  "timezone": "America/Argentina/Buenos_Aires"
}
```

Response `200 OK`: business actualizado.

Notas:

- `slug`, `status` y `onboarding_status` no se editan desde este endpoint en MVP.
- El backend recalcula `onboarding_status` cuando cambian recursos que afectan READY.

## Booking settings

### GET `/api/v1/booking-settings`

Admin autenticado.

Response `200 OK`:

```json
{
  "public_booking_enabled": true,
  "requires_customer_login": false,
  "booking_window_days": 7,
  "min_notice_hours": 3,
  "cancellation_notice_hours": 3,
  "slot_interval_minutes": 30,
  "manual_confirmation_enabled": true,
  "whatsapp_reminders_enabled": false,
  "created_at": "2026-04-28T13:00:00Z",
  "updated_at": "2026-04-28T13:00:00Z"
}
```

### PATCH `/api/v1/booking-settings`

Admin autenticado.

Request:

```json
{
  "public_booking_enabled": true,
  "booking_window_days": 14,
  "min_notice_hours": 3,
  "cancellation_notice_hours": 3,
  "slot_interval_minutes": 30,
  "manual_confirmation_enabled": true
}
```

Response `200 OK`: settings actualizados.

Reglas:

- `requires_customer_login` queda siempre `false` en MVP. Si se envia `true`, responder `400 VALIDATION_ERROR`.
- `whatsapp_reminders_enabled` queda `false` en MVP o se ignora como post-MVP.
- `slot_interval_minutes` debe ser positivo y recomendado dentro de valores controlados: `15`, `30`, `45`, `60`.

## Business hours

### GET `/api/v1/business-hours`

Admin autenticado.

Response `200 OK`:

```json
{
  "data": [
    {
      "id": 1,
      "day_of_week": "TUESDAY",
      "opens_at": "09:00",
      "closes_at": "20:00",
      "is_closed": false
    }
  ]
}
```

### PUT `/api/v1/business-hours`

Admin autenticado. Reemplaza la semana completa.

Request:

```json
{
  "hours": [
    {
      "day_of_week": "MONDAY",
      "opens_at": null,
      "closes_at": null,
      "is_closed": true
    },
    {
      "day_of_week": "TUESDAY",
      "opens_at": "09:00",
      "closes_at": "20:00",
      "is_closed": false
    }
  ]
}
```

Response `200 OK`: semana completa actualizada.

Reglas:

- Deben existir los 7 dias o el backend completa los faltantes con cerrado. Decision recomendada: exigir 7 dias para evitar ambiguedad.
- Si `is_closed = false`, `opens_at` y `closes_at` son obligatorios.
- `opens_at` debe ser menor que `closes_at`.

## Staff members

### GET `/api/v1/staff-members`

Admin autenticado.

Query params:

```text
status=ACTIVE|INACTIVE
q=mateo
page=0
size=20
sort=name,asc
```

Response `200 OK`: page de `StaffMemberSummary`.

### POST `/api/v1/staff-members`

Admin autenticado.

Request:

```json
{
  "name": "Mateo Ruiz",
  "role_label": "Barbero",
  "specialty": "Cortes clasicos y barba",
  "avatar_url": "https://...",
  "user_id": 1
}
```

Response `201 Created`:

```json
{
  "id": 1,
  "name": "Mateo Ruiz",
  "role_label": "Barbero",
  "specialty": "Cortes clasicos y barba",
  "avatar_url": "https://...",
  "status": "ACTIVE",
  "user_id": 1,
  "created_at": "2026-04-28T13:00:00Z",
  "updated_at": "2026-04-28T13:00:00Z"
}
```

Reglas:

- Si se informa `user_id`, debe pertenecer al mismo negocio y no estar vinculado a otro staff member.
- Al crear, el backend crea `staff_working_hours` copiando `business_hours`.

### GET `/api/v1/staff-members/{staff_member_id}`

Admin autenticado. Devuelve detalle.

### PATCH `/api/v1/staff-members/{staff_member_id}`

Admin autenticado.

Request:

```json
{
  "name": "Mateo Ruiz",
  "role_label": "Barbero senior",
  "specialty": "Cortes clasicos y barba",
  "avatar_url": "https://...",
  "status": "ACTIVE"
}
```

Response `200 OK`: staff actualizado.

### DELETE `/api/v1/staff-members/{staff_member_id}`

Admin autenticado. Baja logica.

Response `204 No Content`.

Reglas:

- No borra historial.
- Setea `status = INACTIVE`.
- Si tiene appointments futuros `PENDING` o `CONFIRMED`, responder `409 CONFLICT` o exigir cancelarlos/reasignarlos primero. Decision recomendada MVP: bloquear baja con turnos futuros activos.

## Staff working hours

### GET `/api/v1/staff-members/{staff_member_id}/working-hours`

Admin autenticado.

Response `200 OK`:

```json
{
  "data": [
    {
      "id": 1,
      "day_of_week": "TUESDAY",
      "starts_at": "09:00",
      "ends_at": "20:00",
      "is_available": true
    }
  ]
}
```

### PUT `/api/v1/staff-members/{staff_member_id}/working-hours`

Admin autenticado. Reemplaza semana completa del profesional.

Request:

```json
{
  "hours": [
    {
      "day_of_week": "MONDAY",
      "starts_at": null,
      "ends_at": null,
      "is_available": false
    },
    {
      "day_of_week": "TUESDAY",
      "starts_at": "09:00",
      "ends_at": "20:00",
      "is_available": true
    }
  ]
}
```

Response `200 OK`: semana completa actualizada.

Reglas:

- `staff_member_id` debe pertenecer al negocio autenticado.
- Si `is_available = true`, `starts_at` y `ends_at` son obligatorios.
- `starts_at` debe ser menor que `ends_at`.

## Service offerings

### GET `/api/v1/service-offerings`

Admin autenticado.

Query params:

```text
status=ACTIVE|INACTIVE
category=Combo
q=corte
page=0
size=20
sort=name,asc
```

Response `200 OK`: page de `ServiceOfferingSummary`.

### POST `/api/v1/service-offerings`

Admin autenticado.

Request:

```json
{
  "name": "Corte + barba",
  "category": "Combo",
  "duration_minutes": 90,
  "price_cents": 1650000
}
```

Response `201 Created`: service detail.

Reglas:

- `duration_minutes > 0`.
- `price_cents >= 0`.
- Crear servicio no lo publica utilmente en booking hasta asociarlo a al menos un staff activo.

### GET `/api/v1/service-offerings/{service_offering_id}`

Admin autenticado.

Response `200 OK`:

```json
{
  "id": 20,
  "name": "Corte + barba",
  "category": "Combo",
  "duration_minutes": 90,
  "price_cents": 1650000,
  "status": "ACTIVE",
  "staff_members": [
    {
      "id": 1,
      "name": "Mateo Ruiz",
      "role_label": "Barbero",
      "specialty": "Cortes clasicos y barba",
      "avatar_url": "https://...",
      "status": "ACTIVE"
    }
  ],
  "created_at": "2026-04-28T13:00:00Z",
  "updated_at": "2026-04-28T13:00:00Z"
}
```

### PATCH `/api/v1/service-offerings/{service_offering_id}`

Admin autenticado.

Request:

```json
{
  "name": "Corte + barba",
  "category": "Combo",
  "duration_minutes": 90,
  "price_cents": 1700000,
  "status": "ACTIVE"
}
```

Response `200 OK`: service actualizado.

Reglas:

- Cambios de precio/duracion no modifican appointments existentes.

### DELETE `/api/v1/service-offerings/{service_offering_id}`

Admin autenticado. Baja logica.

Response `204 No Content`.

Reglas:

- Setea `status = INACTIVE`.
- No borra historial.

## Asociacion staff-service

### GET `/api/v1/staff-members/{staff_member_id}/service-offerings`

Admin autenticado.

Response `200 OK`:

```json
{
  "data": [
    {
      "id": 20,
      "name": "Corte + barba",
      "category": "Combo",
      "duration_minutes": 90,
      "price_cents": 1650000,
      "status": "ACTIVE"
    }
  ]
}
```

### PUT `/api/v1/staff-members/{staff_member_id}/service-offerings`

Admin autenticado. Reemplaza asociaciones del profesional.

Request:

```json
{
  "service_offering_ids": [20, 21]
}
```

Response `200 OK`:

```json
{
  "staff_member_id": 1,
  "service_offering_ids": [20, 21]
}
```

Reglas:

- Staff y servicios deben pertenecer al negocio autenticado.
- Servicios inactivos pueden rechazarse para nuevas asociaciones. Decision recomendada MVP: rechazar `INACTIVE` con `400 VALIDATION_ERROR`.
- Remover una asociacion no modifica appointments existentes.

## Customers

### GET `/api/v1/customers`

Admin autenticado.

Query params:

```text
status=ACTIVE|INACTIVE
q=santiago
page=0
size=20
sort=name,asc
```

Response `200 OK`: page de `CustomerSummary`.

### POST `/api/v1/customers`

Admin autenticado.

Request:

```json
{
  "name": "Santiago Moreno",
  "email": "santiago.moreno@mail.demo",
  "phone_number": "+54 11 5555-5555",
  "internal_notes": "Prefiere corte bajo.",
  "status": "ACTIVE"
}
```

Response `201 Created`: customer detail.

### GET `/api/v1/customers/{customer_id}`

Admin autenticado.

Response `200 OK`:

```json
{
  "id": 10,
  "name": "Santiago Moreno",
  "email": "santiago.moreno@mail.demo",
  "phone_number": "+54 11 5555-5555",
  "status": "ACTIVE",
  "internal_notes": "Prefiere corte bajo.",
  "created_at": "2026-04-28T13:00:00Z",
  "updated_at": "2026-04-28T13:00:00Z"
}
```

### PATCH `/api/v1/customers/{customer_id}`

Admin autenticado.

Request:

```json
{
  "name": "Santiago Moreno",
  "email": "santiago.moreno@mail.demo",
  "phone_number": "+54 11 5555-5555",
  "internal_notes": "Prefiere corte bajo.",
  "status": "ACTIVE"
}
```

Response `200 OK`: customer actualizado.

### DELETE `/api/v1/customers/{customer_id}`

Admin autenticado. Baja logica.

Response `204 No Content`.

Reglas:

- Setea `status = INACTIVE`.
- No borra historial.

## Appointments admin

### GET `/api/v1/appointments`

Admin autenticado.

Query params:

```text
date=2026-04-28
from=2026-04-28T00:00:00-03:00
to=2026-04-29T00:00:00-03:00
status=PENDING,CONFIRMED
staff_member_id=1
customer_id=10
service_offering_id=20
source=ADMIN|PUBLIC_BOOKING
page=0
size=50
sort=starts_at,asc
```

Reglas:

- Para agenda diaria, usar `date` o `from/to`.
- Si se envia `date`, el backend interpreta el dia en `business.timezone`.
- `PENDING` y `CONFIRMED` bloquean disponibilidad.

Response `200 OK`: page o lista de appointments. Para agenda diaria se recomienda lista sin paginacion si el rango es un dia.

Appointment response:

```json
{
  "id": 100,
  "customer": {
    "id": 10,
    "name": "Santiago Moreno",
    "email": "santiago.moreno@mail.demo",
    "phone_number": "+54 11 5555-5555",
    "status": "ACTIVE"
  },
  "service_offering": {
    "id": 20,
    "name": "Corte + barba",
    "category": "Combo",
    "duration_minutes": 90,
    "price_cents": 1650000,
    "status": "ACTIVE"
  },
  "staff_member": {
    "id": 1,
    "name": "Mateo Ruiz",
    "role_label": "Barbero",
    "specialty": "Cortes clasicos y barba",
    "avatar_url": "https://...",
    "status": "ACTIVE"
  },
  "starts_at": "2026-04-28T15:30:00-03:00",
  "ends_at": "2026-04-28T17:00:00-03:00",
  "duration_minutes": 90,
  "price_cents": 1650000,
  "status": "CONFIRMED",
  "source": "ADMIN",
  "customer_notes": null,
  "internal_notes": "Cliente pidio mantener largo arriba.",
  "cancellation_reason": null,
  "created_at": "2026-04-28T13:00:00Z",
  "updated_at": "2026-04-28T13:00:00Z"
}
```

### POST `/api/v1/appointments`

Admin autenticado. Crea turno manual.

Request con cliente existente:

```json
{
  "customer_id": 10,
  "service_offering_id": 20,
  "staff_member_id": 1,
  "starts_at": "2026-04-28T15:30:00-03:00",
  "status": "CONFIRMED",
  "customer_notes": null,
  "internal_notes": "Cliente pidio mantener largo arriba."
}
```

Request con cliente rapido:

```json
{
  "customer": {
    "name": "Santiago Moreno",
    "email": "santiago.moreno@mail.demo",
    "phone_number": "+54 11 5555-5555"
  },
  "service_offering_id": 20,
  "staff_member_id": 1,
  "starts_at": "2026-04-28T15:30:00-03:00",
  "status": "CONFIRMED"
}
```

Response `201 Created`: appointment.

Reglas:

- Enviar `customer_id` o `customer`, no ambos.
- `status` inicial permitido desde admin: `PENDING` o `CONFIRMED`.
- Backend calcula `ends_at`, `duration_minutes` y `price_cents`.
- Backend valida que el staff ofrezca el servicio.
- Backend valida disponibilidad y solapamiento.

Errores:

- `409 SLOT_UNAVAILABLE`: horario ocupado.
- `409 STAFF_SERVICE_MISMATCH`: profesional no ofrece el servicio.

### GET `/api/v1/appointments/{appointment_id}`

Admin autenticado.

Response `200 OK`: appointment.

### PATCH `/api/v1/appointments/{appointment_id}`

Admin autenticado. Edita turno.

Request:

```json
{
  "customer_id": 10,
  "service_offering_id": 20,
  "staff_member_id": 1,
  "starts_at": "2026-04-28T16:00:00-03:00",
  "status": "CONFIRMED",
  "customer_notes": "Llega 10 minutos tarde.",
  "internal_notes": "Mantener largo arriba.",
  "cancellation_reason": null
}
```

Response `200 OK`: appointment actualizado.

Reglas:

- Si cambia servicio, recalcular `ends_at`, `duration_minutes` y `price_cents`.
- Validar solapamiento excluyendo el mismo appointment.
- Transiciones invalidas responden `409 INVALID_STATE_TRANSITION`.

### POST `/api/v1/appointments/{appointment_id}/confirm`

Admin autenticado.

Response `200 OK`: appointment confirmado.

Reglas:

- Solo `PENDING` puede pasar a `CONFIRMED`.

### POST `/api/v1/appointments/{appointment_id}/cancel`

Admin autenticado.

Request:

```json
{
  "cancellation_reason": "Cliente aviso que no puede asistir"
}
```

Response `200 OK`: appointment cancelado.

Reglas:

- Setea `status = CANCELLED`.
- No borra appointment.

### POST `/api/v1/appointments/{appointment_id}/complete`

Admin autenticado.

Response `200 OK`: appointment completado.

### POST `/api/v1/appointments/{appointment_id}/no-show`

Admin autenticado.

Response `200 OK`: appointment marcado como `NO_SHOW`.

## Availability/slots admin

### GET `/api/v1/availability/slots`

Admin autenticado. Calcula slots para crear/editar turno desde admin. Soporta consulta diaria o por rango.

Query params:

```text
date=2026-04-28
service_offering_id=20
staff_member_id=1
exclude_appointment_id=100
```

Para vista semanal o rango:

```text
from=2026-04-28
to=2026-05-04
service_offering_id=20
staff_member_id=1
exclude_appointment_id=100
```

Reglas de query:

- Usar `date` para un solo dia.
- Usar `from` y `to` para rango.
- No enviar `date` junto con `from/to`.
- `from` y `to` son fechas en timezone del negocio.
- Rango maximo recomendado MVP: 31 dias.
- Si el rango se usa para booking publico, tambien debe respetar `booking_settings.booking_window_days`.

Response diaria `200 OK`:

```json
{
  "date": "2026-04-28",
  "service_offering_id": 20,
  "staff_member_id": 1,
  "timezone": "America/Argentina/Buenos_Aires",
  "slots": [
    {
      "starts_at": "2026-04-28T15:30:00-03:00",
      "ends_at": "2026-04-28T17:00:00-03:00",
      "available": true
    }
  ]
}
```

Response por rango `200 OK`:

```json
{
  "from": "2026-04-28",
  "to": "2026-05-04",
  "service_offering_id": 20,
  "staff_member_id": 1,
  "timezone": "America/Argentina/Buenos_Aires",
  "days": [
    {
      "date": "2026-04-28",
      "slots": [
        {
          "starts_at": "2026-04-28T15:30:00-03:00",
          "ends_at": "2026-04-28T17:00:00-03:00",
          "available": true
        }
      ]
    },
    {
      "date": "2026-04-29",
      "slots": []
    }
  ]
}
```

Reglas:

- Usa `business_hours`, `staff_working_hours`, `availability_exceptions`, appointments bloqueantes y duracion del servicio.
- `exclude_appointment_id` se usa al editar para no bloquearse contra el mismo appointment.

## Public booking

### GET `/api/v1/public/businesses/{business_slug}/booking-profile`

Publico. Devuelve datos minimos del negocio para la pantalla de booking.

Response `200 OK`:

```json
{
  "business": {
    "name": "Barber Studio",
    "slug": "barber-studio",
    "industry": "Barberia premium",
    "timezone": "America/Argentina/Buenos_Aires"
  },
  "booking_settings": {
    "public_booking_enabled": true,
    "booking_window_days": 7,
    "min_notice_hours": 3,
    "slot_interval_minutes": 30,
    "manual_confirmation_enabled": true
  }
}
```

Errores:

- `404 NOT_FOUND`: slug inexistente.
- `403 PUBLIC_BOOKING_DISABLED`: negocio inactivo o booking deshabilitado.

### GET `/api/v1/public/businesses/{business_slug}/services`

Publico. Lista servicios activos reservables.

Query params:

```text
category=Combo
```

Response `200 OK`:

```json
{
  "data": [
    {
      "id": 20,
      "name": "Corte + barba",
      "category": "Combo",
      "duration_minutes": 90,
      "price_cents": 1650000,
      "staff_members": [
        {
          "id": 1,
          "name": "Mateo Ruiz",
          "role_label": "Barbero",
          "specialty": "Cortes clasicos y barba",
          "avatar_url": "https://..."
        }
      ]
    }
  ]
}
```

Reglas:

- Solo servicios `ACTIVE`.
- Solo incluir servicios con al menos un staff `ACTIVE` asociado.
- No exponer notas internas ni status innecesarios.

### GET `/api/v1/public/businesses/{business_slug}/availability`

Publico. Devuelve slots disponibles para un servicio.

Query params:

```text
date=2026-04-28
service_offering_id=20
staff_member_id=1
```

Para "Cualquiera disponible":

```text
staff_member_id=any
```

Response `200 OK`:

```json
{
  "date": "2026-04-28",
  "service_offering_id": 20,
  "staff_member_id": "any",
  "timezone": "America/Argentina/Buenos_Aires",
  "slots": [
    {
      "starts_at": "2026-04-28T15:30:00-03:00",
      "ends_at": "2026-04-28T17:00:00-03:00",
      "available_staff_member_ids": [1, 2]
    }
  ]
}
```

Reglas:

- Respeta `booking_window_days` y `min_notice_hours`.
- Respeta `slot_interval_minutes`.
- Excluye slots ocupados por appointments `PENDING` o `CONFIRMED`.
- Si `staff_member_id` es numerico, debe ofrecer el servicio.
- Si `staff_member_id=any`, el backend devuelve slots donde exista al menos un staff asignable.

### POST `/api/v1/public/businesses/{business_slug}/appointments`

Publico. Crea reserva sin login.

Request con profesional especifico:

```json
{
  "service_offering_id": 20,
  "staff_member_id": 1,
  "starts_at": "2026-04-28T15:30:00-03:00",
  "customer": {
    "name": "Santiago Moreno",
    "email": "santiago.moreno@mail.demo",
    "phone_number": "+54 11 5555-5555"
  },
  "customer_notes": "Primera vez en el local."
}
```

Request con cualquiera disponible:

```json
{
  "service_offering_id": 20,
  "staff_member_id": "any",
  "starts_at": "2026-04-28T15:30:00-03:00",
  "customer": {
    "name": "Santiago Moreno",
    "email": "santiago.moreno@mail.demo",
    "phone_number": "+54 11 5555-5555"
  }
}
```

Response `201 Created`:

```json
{
  "appointment": {
    "id": 100,
    "service_offering": {
      "id": 20,
      "name": "Corte + barba",
      "category": "Combo",
      "duration_minutes": 90,
      "price_cents": 1650000
    },
    "staff_member": {
      "id": 1,
      "name": "Mateo Ruiz",
      "role_label": "Barbero",
      "specialty": "Cortes clasicos y barba",
      "avatar_url": "https://..."
    },
    "starts_at": "2026-04-28T15:30:00-03:00",
    "ends_at": "2026-04-28T17:00:00-03:00",
    "status": "PENDING",
    "source": "PUBLIC_BOOKING"
  },
  "customer": {
    "name": "Santiago Moreno",
    "email": "santiago.moreno@mail.demo",
    "phone_number": "+54 11 5555-5555"
  },
  "cancellation": {
    "token": "public-cancel-token",
    "expires_at": "2026-04-28T12:30:00-03:00"
  }
}
```

Reglas:

- Backend busca o crea customer dentro del negocio.
- Backend revalida disponibilidad al confirmar.
- Si `staff_member_id=any`, backend asigna un staff real antes de guardar.
- `status = PENDING` si `manual_confirmation_enabled = true`.
- `status = CONFIRMED` si `manual_confirmation_enabled = false`.
- Backend genera token publico `CANCEL`, guarda hash y devuelve token plano solo en esta respuesta.
- No aceptar `price_cents`, `duration_minutes`, `ends_at`, `status` ni `source` desde el cliente publico.

Errores:

- `403 PUBLIC_BOOKING_DISABLED`.
- `400 VALIDATION_ERROR`.
- `409 SLOT_UNAVAILABLE`.
- `409 STAFF_SERVICE_MISMATCH`.

## Public cancellation token

### GET `/api/v1/public/cancellations/{token}`

Publico. Valida token y devuelve el resumen del appointment antes de cancelarlo. No cambia estado.

Response `200 OK`:

```json
{
  "appointment": {
    "service_name": "Corte + barba",
    "staff_member_name": "Mateo Ruiz",
    "starts_at": "2026-04-28T15:30:00-03:00",
    "ends_at": "2026-04-28T17:00:00-03:00",
    "status": "CONFIRMED"
  },
  "business": {
    "name": "Barber Studio",
    "timezone": "America/Argentina/Buenos_Aires"
  },
  "can_cancel": true
}
```

Notas:

- `status` representa el estado actual del appointment antes de cancelar. Puede ser `PENDING` o `CONFIRMED`.
- Si el appointment ya esta `CANCELLED`, el endpoint debe responder error de estado invalido o `can_cancel = false`, segun la decision de UX. Decision recomendada MVP: responder `409 INVALID_STATE_TRANSITION`.

Errores:

- `404 TOKEN_INVALID`.
- `409 TOKEN_USED`.
- `409 TOKEN_EXPIRED`.
- `409 INVALID_STATE_TRANSITION`: appointment ya cancelado o no cancelable.

### POST `/api/v1/public/cancellations/{token}`

Publico. Cancela appointment autorizado por token.

Request:

```json
{
  "cancellation_reason": "No puedo asistir"
}
```

Response `200 OK`:

```json
{
  "status": "CANCELLED",
  "message": "Tu turno fue cancelado"
}
```

Reglas:

- Hashear token recibido y buscar `appointment_public_tokens.token_hash`.
- Validar `type = CANCEL`, `used_at = null`, `expires_at > now`.
- Validar `booking_settings.cancellation_notice_hours`.
- Setear appointment `CANCELLED`.
- Guardar `cancellation_reason` si se informa.
- Completar `used_at`.

## Reglas READY/onboarding

El backend puede recalcular `businesses.onboarding_status` luego de cambios en:

- Business base.
- Booking settings.
- Business hours.
- Staff members.
- Staff working hours.
- Service offerings.
- Staff service offerings.

Criterio `READY` segun flujos:

- Business `ACTIVE`.
- Owner asignado.
- Booking settings existente.
- Business hours existente.
- Al menos un staff activo.
- Staff working hours para ese staff.
- Al menos un service offering activo.
- Al menos una relacion staff-service.

El contrato no expone un endpoint manual `mark-ready` en MVP. `onboarding_status` es derivado/controlado por backend.

## Resumen de endpoints

```text
POST   /api/v1/auth/google
GET    /api/v1/auth/me
POST   /api/v1/auth/logout

GET    /api/v1/business
PATCH  /api/v1/business

GET    /api/v1/booking-settings
PATCH  /api/v1/booking-settings

GET    /api/v1/business-hours
PUT    /api/v1/business-hours

GET    /api/v1/staff-members
POST   /api/v1/staff-members
GET    /api/v1/staff-members/{staff_member_id}
PATCH  /api/v1/staff-members/{staff_member_id}
DELETE /api/v1/staff-members/{staff_member_id}
GET    /api/v1/staff-members/{staff_member_id}/working-hours
PUT    /api/v1/staff-members/{staff_member_id}/working-hours
GET    /api/v1/staff-members/{staff_member_id}/service-offerings
PUT    /api/v1/staff-members/{staff_member_id}/service-offerings

GET    /api/v1/service-offerings
POST   /api/v1/service-offerings
GET    /api/v1/service-offerings/{service_offering_id}
PATCH  /api/v1/service-offerings/{service_offering_id}
DELETE /api/v1/service-offerings/{service_offering_id}

GET    /api/v1/customers
POST   /api/v1/customers
GET    /api/v1/customers/{customer_id}
PATCH  /api/v1/customers/{customer_id}
DELETE /api/v1/customers/{customer_id}

GET    /api/v1/appointments
POST   /api/v1/appointments
GET    /api/v1/appointments/{appointment_id}
PATCH  /api/v1/appointments/{appointment_id}
POST   /api/v1/appointments/{appointment_id}/confirm
POST   /api/v1/appointments/{appointment_id}/cancel
POST   /api/v1/appointments/{appointment_id}/complete
POST   /api/v1/appointments/{appointment_id}/no-show

GET    /api/v1/availability/slots

GET    /api/v1/public/businesses/{business_slug}/booking-profile
GET    /api/v1/public/businesses/{business_slug}/services
GET    /api/v1/public/businesses/{business_slug}/availability
POST   /api/v1/public/businesses/{business_slug}/appointments
GET    /api/v1/public/cancellations/{token}
POST   /api/v1/public/cancellations/{token}
```

## Pendientes fuera de este contrato

- Contrato CRUD de `availability_exceptions`, si se decide incluirlo en el MVP operativo.
- Politica CSRF final segun arquitectura frontend/backend desplegada.

Notas:

- El plan de migracion por PRs vive en `plan-migracion-backend-mvp.md`.
- Las migraciones DB se implementan con Flyway segun `plan-migracion-backend-mvp.md`.
- No se crea documento propio para bajar el algoritmo de disponibilidad con mas detalle; el dev lo define durante implementacion usando estos contratos, `flujos-funcionales-mvp.md`, `schema-db-mvp.md` y tests.
