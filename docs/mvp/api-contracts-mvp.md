# Turnero API - Contratos HTTP MVP

Actualizado: 2026-09-03

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

### Frontera HTTP: BFF, CORS y CSRF (TURN-62)

Esta seccion es la fuente canonica de la decision HTTP/BFF/CORS/CSRF del MVP. Distingue la responsabilidad acordada de la mitigacion efectivamente implementada.

```text
Browser admin -> /api/backend/... (Next.js BFF same-origin) -> /api/v1/... (Spring)
Browser booking publico -> mismo BFF same-origin -> Spring (al integrar la UI)
```

Inventario al 2026-10-05, segun la auditoria frontend aportada para TURN-62 y la inspeccion de este backend:

| Superficie | Recorrido browser | Consumidor browser directo de Spring |
| --- | --- | --- |
| Admin implementado | apiFetch usa rutas relativas /api/backend; el BFF llama a Spring con TURNERO_API_BASE_URL server-side | Ninguno identificado ni aprobado |
| Booking publico | UI todavia sin integrar; el BFF admite profile, services, availability, create appointment, get cancellation y cancel | Ninguno identificado ni aprobado; la integracion debe conservar el BFF |

El BFF reenvia Cookie a Spring y devuelve Set-Cookie al navegador. La cookie recibida mediante el BFF queda asociada al host de su respuesta, sin Domain; el navegador no necesita conocer la URL de Spring.

**CORS:** Spring permanece sin configurar CORS en todos los perfiles. No se agregan @CrossOrigin, CorsConfiguration, headers Access-Control-Allow-* ni configuracion equivalente. Las llamadas server-side del BFF no necesitan CORS. Un futuro consumidor browser cross-origin aprobado exige otra decision con origenes explicitos, metodos, headers y credentials segun su contrato. La ausencia de CORS no es una restriccion de acceso de red ni una mitigacion CSRF.

**Responsabilidades:** el BFF es la frontera responsable de validar procedencia/origen y mitigar CSRF en las mutaciones recibidas desde el navegador. Spring conserva autenticacion, autorizacion, business scoping y reglas de negocio; en cancelacion publica valida el token de cancelacion.

**Estado actual:** la mitigacion explicita de procedencia/CSRF del BFF todavia NO esta implementada. Segun la auditoria frontend aportada, el proxy solo limita rutas y metodos: no valida Origin/Referer ni usa token CSRF o Fetch Metadata. Esa allowlist no acredita la mitigacion. Spring tampoco tiene proteccion CSRF explicita. SameSite=Lax es una defensa complementaria y no reemplaza la mitigacion CSRF.

#### Matriz de mutaciones y frontera responsable

Las rutas siguientes identifican las operaciones Spring actuales. Toda operacion que el browser consuma debe pasar por una ruta admitida del BFF; el inventario backend no implica que todas tengan UI o esten habilitadas por el proxy. La validacion de procedencia corresponde al BFF y esta pendiente en todas las filas.

| Mutacion Spring | Frontera de procedencia/CSRF | Responsabilidad Spring |
| --- | --- | --- |
| POST /api/v1/auth/google | BFF; pendiente, incluido login | Validar identidad Google y acceso admin; crear sesion |
| POST /api/v1/auth/logout | BFF; pendiente, incluso sin sesion valida | Revocar sesion si corresponde y expirar cookie de forma idempotente |
| PATCH /api/v1/business | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| PATCH /api/v1/booking-settings | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| PUT /api/v1/business-hours | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| POST /api/v1/service-offerings; PATCH/DELETE /api/v1/service-offerings/{id} | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| POST /api/v1/staff-members; PATCH/DELETE /api/v1/staff-members/{id} | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| PUT /api/v1/staff-members/{id}/working-hours; PUT /api/v1/staff-members/{staff_member_id}/service-offerings | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| POST /api/v1/customers; PATCH/DELETE /api/v1/customers/{id} | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| POST /api/v1/appointments; PATCH/DELETE /api/v1/appointments/{id} | BFF; pendiente | Autenticacion, autorizacion, scoping y reglas de negocio |
| POST /api/v1/appointments/{id}/confirm; POST /api/v1/appointments/{id}/cancel; POST /api/v1/appointments/{id}/complete; POST /api/v1/appointments/{id}/no-show | BFF; pendiente | Autenticacion, autorizacion, scoping y transiciones de estado |
| POST /api/v1/public/businesses/{business_slug}/appointments | BFF al integrar booking; pendiente | Reglas de reserva publica y resolucion del negocio; sin sesion admin |
| POST /api/v1/public/cancellations/{token} | BFF al integrar booking; pendiente | Autorizar por token de cancelacion y aplicar reglas; sin sesion admin |

Las lecturas GET de auth, admin y booking conservan el recorrido BFF; no son mutaciones. La validacion de procedencia de booking publico es independiente de la sesion admin y no sustituye la autorizacion por token ni el rate limiting de TURN-112.

#### Entrega documental y seguimiento

TURN-62 fija esta decision sin cambiar comportamiento HTTP, codigo ni configuracion Spring; no requiere tests backend nuevos. No acredita mitigacion CSRF operativa ni cierre del hardening de TURN-32.

La implementacion se separa en una subtarea frontend/BFF **pendiente de crear/vincular**, sin ID asignado en este documento. Debe definir el mecanismo concreto de validacion de procedencia, tratamiento de headers ausentes o null y origen esperado en local/HTTPS y detras de proxies; cubrir login/logout, mutaciones admin y mutaciones publicas admitidas; y probar solicitudes aceptadas/rechazadas antes de considerar implementada la mitigacion. El comportamiento browser/BFF de cookies en local y HTTPS requiere validacion de integracion en esa subtarea. El inventario de brechas vive en [deuda tecnica](deuda-tecnica-backend.md#frontera-http-y-mitigacion-pendiente-en-el-bff); el estado de entrega vive en [tracking](tracking-implementacion-mvp.md#hardening-operativo-final).

### Admin autenticado

Endpoints bajo estas familias requieren cookie de sesion Turnero, salvo logout: POST /api/v1/auth/logout tambien responde 204 y expira la cookie si falta o la sesion ya no es valida:

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

- Autenticacion por cookie HTTP-only. En local se usa `turnero_session`; en HTTPS productivo, `__Host-turnero_session`.
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
UNAUTHORIZED
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
  "status": "ACTIVE",
  "public_booking_blocked": false
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

- `401 UNAUTHORIZED`: token Google invalido, `email_verified = false` o usuario no aprovisionado.
- `403 FORBIDDEN`: usuario sin `business_id` o con rol no permitido.

### GET `/api/v1/auth/me`

Admin autenticado. Devuelve sesion actual.

Response `200 OK`: mismo body que login.

Errores:

- `401 UNAUTHORIZED`: sin cookie, sesion vencida o revocada.

### POST `/api/v1/auth/logout`

Admin autenticado. Revoca la sesion actual. El logout es idempotente: si la cookie no existe o la sesion ya no es valida, igualmente expira la cookie y responde `204`.

Response `204 No Content`.

Headers:

```http
Set-Cookie: __Host-turnero_session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
```

### Cookie por ambiente

AuthController emite HttpOnly=true, Path=/ y sin Domain tanto al crear como al expirar la cookie. Max-Age de login se calcula desde auth.session.ttl-days; logout usa Max-Age=0. El frontend no lee la cookie ni persiste tokens en JavaScript; el BFF reenvia Set-Cookie.

Defaults versionados, sin overrides externos:

| Perfil | Nombre | Secure | SameSite | TTL de login |
| --- | --- | --- | --- | --- |
| Base / dev | turnero_session | false | Lax | 7 dias (Max-Age=604800) |
| desa | turnero_session (heredado) | false (heredado) | Lax (heredado) | 7 dias (heredado) |
| prod | __Host-turnero_session | true | Lax | 7 dias (heredado) |

Configuracion externa explicitamente referenciada por los YAML:

- Base, dev y desa: AUTH_SESSION_SECURE reemplaza el default false. No hay placeholders AUTH_SESSION_COOKIE_NAME, AUTH_SESSION_SAME_SITE ni AUTH_SESSION_TTL_DAYS en esos perfiles.
- prod: AUTH_SESSION_COOKIE_NAME, AUTH_SESSION_SECURE y AUTH_SESSION_SAME_SITE reemplazan sus defaults. TTL no tiene placeholder AUTH_SESSION_TTL_DAYS en el YAML versionado.
- Las propiedades auth.session.cookie-name, auth.session.ttl-days, auth.session.secure y auth.session.same-site pueden sobrescribirse por mecanismos externos de configuracion Spring. Eso no significa que las cuatro variables AUTH_SESSION_* anteriores sean aliases uniformes en todos los perfiles. No se verifico la configuracion efectiva de un despliegue.

HTTP local usa turnero_session con Secure=false. Para HTTPS se debe configurar Secure=true; desa no lo activa automaticamente. En prod HTTPS se conserva __Host-turnero_session con Secure=true, Path=/ y sin Domain; cualquier override debe respetar las restricciones del prefijo __Host-. Para usar ese nombre en desa hace falta configurar externamente auth.session.cookie-name; el perfil no referencia AUTH_SESSION_COOKIE_NAME.

Secure depende del HTTPS visible al navegador/BFF, no del protocolo del salto interno BFF -> Spring. SameSite=Lax es complementario y no reemplaza la mitigacion CSRF pendiente descrita en [la decision HTTP](#frontera-http-bff-cors-y-csrf-turn-62).

### Estado de implementacion observado al 2026-10-05

El estado de implementacion observado para TURN-62 incluye el contrato auth alineado, cookie por perfil y logout idempotente. Esta observacion no acredita un merge adicional ni un cierre en Jira. La decision HTTP de TURN-62 es documental; la mitigacion del BFF sigue pendiente en una subtarea separada de frontend/BFF por crear/vincular. El estado de entrega vive en [tracking](tracking-implementacion-mvp.md).

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

- Deben enviarse exactamente los 7 dias, sin faltantes ni duplicados.
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

Búsqueda parcial sin distinguir mayúsculas por nombre, rol y especialidad; combinable con estado y limitada al negocio autenticado. Se escapan comodines SQL. `page` >= 0, `size` 1–100 (predeterminado 20), `q` máximo 100 caracteres. Orden permitido: `name,asc` (predeterminado) o `name,desc`, siempre con `id,asc` como desempate. Parámetros inválidos: `400`.

Response `200 OK` (reemplaza el array anterior):

```json
{"data":[{"id":1,"business_id":1,"user_id":null,"name":"Ana","role_label":null,"specialty":null,"avatar_url":null,"status":"ACTIVE","service_count":0}],"page":{"number":0,"size":20,"total_elements":1,"total_pages":1}}
```

`service_count` se incluye en cada elemento del listado: cantidad de servicios asignados, incluidos los inactivos, excluyendo eliminados y servicios ajenos al negocio. Sin asignaciones vale 0. Se calcula mediante una única consulta agregada para los IDs de la página; no carga el catálogo ni consulta por profesional. Este campo derivado no se incluye en detalle/alta/edición ni en `/me`.

Conserva los demás campos de `StaffMemberResponseDto`; sin resultados devuelve `data: []`. Excluye profesionales eliminados (`deleted_at`), incluso con filtro INACTIVE. Integrar backend antes del frontend.

### GET `/api/v1/staff-members/me`

Admin autenticado. Devuelve el profesional vinculado al usuario de sesión dentro del negocio autenticado, incluyendo inactivos, excluyendo eliminados y sin depender de búsqueda, estado ni paginación. No recibe un ID de usuario del cliente.

Response `200 OK`: `{ "staff_member": StaffMemberResponseDto | null }`. `null` significa que el usuario todavía no tiene profesional vinculado. Sin sesión: `401`.

El formulario usa esta consulta para deshabilitar la vinculación cuando ya existe o mientras no se pudo comprobar. El POST conserva `409 STAFF_USER_ALREADY_LINKED` como protección ante concurrencia.

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

- Si se informa `user_id`, debe pertenecer al mismo negocio y no estar vinculado a otro profesional no eliminado. Ante una vinculación existente (incluido profesional inactivo), responde `409` con `code: STAFF_USER_ALREADY_LINKED`. La creación se serializa por usuario y es transaccional junto con los horarios.
- `name`: obligatorio, hasta 100 caracteres. `specialty`: obligatoria al crear, hasta 150. `role_label`: opcional, hasta 80 caracteres tanto en POST como en PATCH; 81 o más devuelve `400` con error de campo. No requiere migraciones.
- Al crear, el backend crea `staff_working_hours` copiando `business_hours`.

### GET `/api/v1/staff-members/{staff_member_id}`

Admin autenticado. Devuelve detalle del profesional del negocio de sesión, también inactivos. Recurso inexistente, eliminado o ajeno devuelve 404; no modifica datos.

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

Admin autenticado. Baja lógica idempotente, respuesta `204 No Content`.

- Setea `status=INACTIVE` sin borrar perfil, vínculo al usuario, servicios, horarios ni historial. No cambia la cuenta ni la sesión del usuario vinculado.
- Si hay turnos `PENDING` o `CONFIRMED` cuyo `starts_at` es posterior al momento actual en la zona horaria del negocio, devuelve `409 STAFF_HAS_FUTURE_APPOINTMENTS` sin cambios parciales. No cancela ni reasigna turnos automáticamente.
- Turnos pasados o con estado `CANCELLED`, `COMPLETED` y `NO_SHOW` no bloquean. Un profesional ya inactivo devuelve 204 sin modificar su timestamp.
- PATCH con `status=INACTIVE` aplica exactamente la misma regla; si se mezclan campos y la baja entra en conflicto, todo el PATCH falla sin modificaciones.
- PATCH con `{ "status": "ACTIVE" }` reactiva y devuelve `200` con el DTO existente. Conserva todas las relaciones; pedir un estado que ya tiene es idempotente.
- GET/PATCH/DELETE usan el negocio autenticado y devuelven `404` para recursos ajenos o inexistentes.
- Cambios de perfil/estado, reservas públicas y creación/reprogramación/reasignación administrativa se coordinan con bloqueo de la fila del profesional. La reserva pública vuelve a leer el estado tras adquirir el bloqueo. Si gana la baja, no puede crearse la reserva; si gana una reserva futura, la baja entra en conflicto.
- La API administrativa rechaza asignaciones a profesionales inactivos con `409 INVALID_STATE_TRANSITION`; permite editar notas históricas. La reserva pública conserva su `403 FORBIDDEN` por profesional inactivo.
- La desactivación conserva el profesional visible bajo Inactivos. Para retirarlo del listado usar el endpoint `/catalog` siguiente.

### DELETE `/api/v1/staff-members/{staff_member_id}/catalog`

Admin autenticado. Borrado lógico definitivo para la interfaz, respuesta `204 No Content`.

- Requiere profesional INACTIVE del negocio autenticado; un activo devuelve `409 INVALID_STATE_TRANSITION`. También protege contra inactivos heredados con turnos futuros PENDING/CONFIRMED (`409 STAFF_HAS_FUTURE_APPOINTMENTS`). Sin cambios parciales.
- Marca `deleted_at` y `updated_at`. Desaparece de todos los filtros, contadores, `/me`, detalle, servicios y horarios operativos. GET/PATCH/DELETE y una segunda eliminación devuelven `404`; no se puede reactivar ni editar desde la app/API operativa.
- Conserva la fila, `user_id`, servicios, horarios y turnos históricos; las lecturas de historial siguen resolviendo el nombre del profesional. No borra la cuenta ni cierra su sesión. La cuenta puede vincularse a un nuevo perfil; solo un perfil no eliminado puede vincularse a cada usuario.
- Usa el mismo bloqueo transaccional que reactivación y reservas: si gana reactivación, eliminación responde 409; si gana eliminación, reactivación responde 404.
- Requiere migración Flyway `V1003__staff_member_logical_removal.sql`: columna nullable, restricción de eliminados inactivos e índice único parcial por usuario para perfiles no eliminados. Se aplica al reiniciar backend, antes de integrar frontend.

## Staff working hours

### GET `/api/v1/staff-members/{staff_member_id}/working-hours`

Admin autenticado, limitado al negocio de sesión. También admite profesionales inactivos.

Response `200 OK`: array directo de siete días, de MONDAY a SUNDAY; sin wrapper ni IDs.

```json
[
  {"day_of_week":"MONDAY","starts_at":"09:00","ends_at":"18:00","is_available":true},
  {"day_of_week":"TUESDAY","starts_at":"09:00","ends_at":"18:00","is_available":true},
  {"day_of_week":"WEDNESDAY","starts_at":"09:00","ends_at":"18:00","is_available":true},
  {"day_of_week":"THURSDAY","starts_at":"09:00","ends_at":"18:00","is_available":true},
  {"day_of_week":"FRIDAY","starts_at":"09:00","ends_at":"18:00","is_available":true},
  {"day_of_week":"SATURDAY","starts_at":null,"ends_at":null,"is_available":false},
  {"day_of_week":"SUNDAY","starts_at":null,"ends_at":null,"is_available":false}
]
```

Los días sin fila persistida se presentan como no disponibles con horas nulas. GET no inserta ni modifica registros. Los días no disponibles siempre se devuelven con horas nulas.

### PUT `/api/v1/staff-members/{staff_member_id}/working-hours`

Admin autenticado. Reemplaza la semana completa; request y response `200 OK` usan el mismo array directo de siete días del ejemplo anterior. El response siempre queda ordenado de MONDAY a SUNDAY.

Reglas:

- Profesional inexistente o de otro negocio: `404 NOT_FOUND` tanto en GET como en PUT.
- Exactamente siete días únicos válidos. Lista, elementos, `day_of_week` e `is_available` no pueden ser nulos.
- Horas en formato estricto `HH:mm`. Si `is_available=true`, inicio y fin son obligatorios y `starts_at < ends_at`; no admite rangos nocturnos.
- Si `is_available=false`, ambas horas se normalizan a `null`.
- Cuerpo mal formado, días duplicados/faltantes o valores inválidos: `400 VALIDATION_ERROR`, sin cambios parciales.
- Escritura transaccional: bloquea la fila del profesional, actualiza horarios existentes conservando sus IDs e inserta únicamente días faltantes. El bloqueo también serializa con la reserva pública.
- La disponibilidad efectiva es la intersección con los horarios del negocio y las demás reglas de reserva. No exige que la semana del profesional esté contenida en la del negocio.
- No modifica turnos futuros ni históricos. No requiere migraciones.

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

`q` busca por nombre y categoría sin distinguir mayúsculas (máximo 100 caracteres); `category` (máximo 255 caracteres) y `status` se combinan con la búsqueda. `page` empieza en `0`, `size` admite `1..100` (por defecto `20`) y `sort` admite `name`, `category`, `duration_minutes`, `price_cents` o `status` con `asc|desc` (por defecto `name,asc`). El ID ascendente desempata siempre. Parámetros inválidos devuelven `400`.

### GET `/api/v1/service-offerings/categories`

Admin autenticado. Devuelve un array ordenado de categorías distintas, no vacías, del negocio autenticado (incluye servicios activos e inactivos).

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

### DELETE `/api/v1/service-offerings/{service_offering_id}/catalog`

Admin autenticado. Elimina del catálogo mediante borrado lógico independiente de desactivar.

Response `204 No Content`. `409 Conflict` si el servicio está activo; `404 Not Found` si no existe, ya fue eliminado o pertenece a otro negocio.

Reglas:

- Requiere `status = INACTIVE` y asigna `deleted_at` / `updated_at`.
- Excluye el servicio de listado, categorías, selección para nuevos turnos, booking público y asociaciones nuevas. Una categoría sigue visible si otro servicio no eliminado la utiliza.
- GET y PATCH del catálogo devuelven 404 para eliminados; no se restauran desde la app.
- Conserva la fila y las referencias históricas de turnos; las consultas históricas y la cancelación de turnos existentes pueden seguir recuperando sus datos.
- Editar, desactivar y eliminar serializan sus cambios con bloqueo de fila dentro de una transacción para evitar una restauración concurrente.

## Asociacion staff-service

### GET `/api/v1/staff-members/{staff_member_id}/service-offerings`

Admin autenticado. Devuelve un **array**, sin wrapper ni paginación, con las asociaciones actuales del profesional dentro del negocio. Incluye inactivos conservados; excluye servicios eliminados del catálogo. Profesional inexistente o ajeno: `404`.

Response `200 OK`:

```json
[
  {
    "id": 20,
    "name": "Corte + barba",
    "category": "Combo",
    "duration_minutes": 90,
    "price_cents": 1650000,
    "status": "ACTIVE"
  }
]
```

### PUT `/api/v1/staff-members/{staff_member_id}/service-offerings`

Admin autenticado. Reemplaza el conjunto completo de asociaciones; admite profesionales activos e inactivos.

```json
{ "service_offering_ids": [20, 21] }
```

Response **`204 No Content`**, sin cuerpo.

- `[]` quita todas las asociaciones. Se normalizan duplicados; orden de IDs irrelevante.
- Lista ausente/nula, elementos nulos o IDs no positivos: `400 VALIDATION_ERROR`.
- Profesional o servicio inexistente, ajeno al negocio o servicio eliminado: `404 NOT_FOUND`.
- Inactivo ya asociado: se puede conservar o quitar. Inactivo nuevo: `409 INVALID_STATE_TRANSITION`.
- La condición de asociación existente se comprueba en la DB al guardar. Tras quitar un inactivo, no puede reasignarse mientras siga inactivo.
- Transacción con bloqueo del profesional y servicios solicitados en orden de ID; validación previa a escrituras, actualización por diferencia y rollback completo ante errores. Reemplazos concurrentes se serializan sin mezclar conjuntos.
- Remover asociaciones no modifica turnos futuros ni históricos. No requiere migraciones.
- El catálogo de selección consume `GET /service-offerings?status=ACTIVE&q=...&page=0&size=20&sort=name,asc`; no descarga todo el catálogo.

## Customers

### GET `/api/v1/customers`

Admin autenticado.

Query params:

```text
status=ACTIVE|INACTIVE|ALL
public_booking_blocked=true|false (opcional)
q=santiago
page=0
size=20
sort=name,asc
```

Response `200 OK` (reemplaza el array anterior):

```json
{"data":[{"id":10,"name":"Santiago Moreno","email":"santiago@example.com","phone_number":"+54 (11) 5555-5555","status":"ACTIVE"}],"page":{"number":0,"size":20,"total_elements":1,"total_pages":1}}
```

- Búsqueda parcial por nombre/email sin distinguir mayúsculas. Las entradas telefónicas con dígitos, espacios, `+`, guiones, puntos o paréntesis comparan solo dígitos; no infieren códigos de país y no alteran el teléfono guardado. Los comodines SQL se tratan como texto literal.
- Búsqueda y estado se combinan, siempre limitados al negocio de la sesión. Filtrado, orden y paginación ocurren en la DB, sin descargar el catálogo completo.
- Defaults: página 0, tamaño 20, orden `name,asc`. Tamaño permitido 1–100, `q` hasta 100 caracteres, estados ACTIVE/INACTIVE/ALL; omitir status devuelve ACTIVE (sin archivar). INACTIVE significa archivado y ALL incluye ambos. public_booking_blocked es independiente del archivo y omitirlo incluye permitidos y bloqueados. Orden alternativo `name,desc`; ambos usan `id,asc` como desempate. Parámetros inválidos devuelven 400.
- El resumen conserva id, nombre, email, teléfono, estado y public_booking_blocked. Nunca incluye public_booking_block_reason. Los contactos nulos se omiten; nunca incluye notas internas ni fechas administrativas. Sin coincidencias: `data: []` y metadatos de página.
- El endpoint conserva autenticación/autorización admin. TURN-102 requiere la migración V1004; integrar backend antes del frontend por los nuevos campos y el filtro de archivo predeterminado. Las consultas internas de clientes mantienen su contrato.

### POST `/api/v1/customers`

Admin autenticado.

Request:

```json
{
  "name": "Santiago Moreno",
  "email": "santiago.moreno@mail.demo",
  "phone_number": "+54 11 5555-5555",
  "internal_notes": "Prefiere corte bajo."
}
```

Response `201 Created`: customer detail.

Reglas de alta y edición (TURN-100):

- Alta: nombre y teléfono obligatorios; email y notas internas opcionales. Límites: nombre 100, teléfono 30, email 150 y notas 2000 caracteres. Email debe ser válido cuando se informa. Teléfono conserva su formato; no se infieren códigos de país.
- `phone_number` es el campo canónico de alta; `phone` se conserva como alias compatible. El estado inicial es ACTIVE, asignado por backend; el negocio proviene de la sesión y no se vincula una cuenta de usuario.
- Se recortan espacios exteriores. Email/notas vacíos se guardan como null y se omiten en respuestas. Las notas conservan saltos de línea internos y no se incluyen en el listado.
- PATCH parcial: omisión o null conserva el valor existente; `email: ""` e `internal_notes: ""` permiten borrarlos. Nombre o teléfono enviados vacíos/blancos devuelven 400. Un cliente antiguo sin teléfono puede editar otros campos omitiendo `phone_number`.
- El formulario TURN-100 envía solo nombre, teléfono, email y notas; no cambia estado, usuario, negocio ni fechas. La capacidad backend existente de cambiar estado se conserva para su flujo específico.
- GET detalle y PATCH usan el negocio autenticado: inexistentes o ajenos responden 404. Validaciones responden 400 con errores por campo. No requiere migraciones y no cambia el booking público.

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

Admin autenticado. Archivo lógico, independiente del bloqueo de reservas públicas.

Response `204 No Content`.

Reglas:

- Setea `status = INACTIVE`.
- Conserva datos, notas y turnos; no cancela reservas ni modifica el bloqueo público.
- PATCH con solo status ACTIVE restaura la ficha, conservando el bloqueo.

### Archivo y bloqueo público — TURN-102

Migración V1004: public_booking_blocked BOOLEAN NOT NULL DEFAULT FALSE y public_booking_block_reason VARCHAR(500) nullable. Los INACTIVE existentes pasan a interpretarse como archivados sin cambiar registros ni relaciones. Crear cliente siempre inicia ACTIVE y sin bloqueo; flags de alta no son editables.

Detalle administrativo incorpora public_booking_blocked y, cuando está bloqueado, public_booking_block_reason. El listado expone solo el indicador. PATCH admite:

```json
{"public_booking_blocked":true,"public_booking_block_reason":"Ausencias reiteradas"}
```

```json
{"public_booking_blocked":false}
```

Bloquear exige motivo interno no blanco de 1–500 caracteres y al menos email o teléfono con dígitos. Desbloquear limpia el motivo vigente. Omitir/null conserva valores; un motivo sin bloqueo produce 400. Ninguna acción cambia el archivo de manera implícita. Ediciones posteriores tampoco pueden dejar una ficha bloqueada sin contacto utilizable.

La creación pública de turnos comprueba contactos antes de persistir. Email: trim y comparación case-insensitive. Teléfono: dígitos exactos, sin inferir país. Cualquier ficha coincidente bloqueada del negocio impide reservar, incluso archivada o con contactos compartidos. No es identidad verificada; nuevos contactos pueden eludir el bloqueo.

- 403 CUSTOMER_BOOKING_BLOCKED: contactar al negocio. Nunca expone el motivo privado.
- 409 CUSTOMER_IDENTITY_AMBIGUOUS: contactar al negocio, sin elegir ni fusionar fichas arbitrariamente.
- Reutilización: email único primero, luego teléfono único; cero coincidencias crea una ficha. Una reserva exitosa restaura al archivado no bloqueado en la misma transacción, conservando nombre, contacto y notas; un fallo posterior revierte la restauración.
- Se bloquean y refrescan las filas coincidentes por ID ascendente para coordinar reserva y cambios administrativos. Los índices por negocio/contacto soportan la búsqueda.
- El bloqueo no afecta turnos existentes, su cancelación ni asignaciones administrativas. La futura agenda real debe advertir al admin; la agenda demo no cambia.

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
DELETE /api/v1/staff-members/{staff_member_id}/catalog
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
- Implementacion de la mitigacion de procedencia/CSRF en el BFF: subtarea frontend/BFF pendiente de crear/vincular. La decision esta definida en [Seguridad y scoping](#frontera-http-bff-cors-y-csrf-turn-62); la mitigacion todavia NO esta implementada.

Notas:

- El plan de migracion por PRs vive en `plan-migracion-backend-mvp.md`.
- Las migraciones DB se implementan con Flyway segun `plan-migracion-backend-mvp.md`.
- No se crea documento propio para bajar el algoritmo de disponibilidad con mas detalle; el dev lo define durante implementacion usando estos contratos, `flujos-funcionales-mvp.md`, `schema-db-mvp.md` y tests.
