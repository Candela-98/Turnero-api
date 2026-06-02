# Turnero API - Google Auth MVP

## Proposito

Este documento define como implementar login con Google en el MVP de Turnero sin depender todavia de Auth0 u otra plataforma externa.

Objetivos:

- Aprender e implementar el flujo de autenticacion propio de forma controlada.
- Usar Google solo como proveedor de identidad.
- Emitir una sesion propia de Turnero para acceder al admin.
- Mantener el modelo preparado para migrar post-MVP a Auth0 u otra plataforma.

No reemplaza a:

- `../schema-db-mvp.md`
- `../flujos-funcionales-mvp.md`
- `../api-contracts-mvp.md`

## Decision MVP

Turnero implementa:

```text
Google Identity Services -> backend valida identidad -> users -> user_sessions -> cookie HTTP-only
```

Google no es la sesion interna de Turnero.

El token de Google se usa una sola vez para probar identidad. Despues, Turnero crea una sesion propia, guarda su hash en DB y envia al navegador una cookie segura.

## Modelo de datos involucrado

El modelo de datos canonical vive en `../schema-db-mvp.md`.

Este flujo usa principalmente:

- `users.auth_provider`
- `users.auth_subject`
- `users.business_id`
- `users.role`
- `user_sessions.session_token_hash`
- `user_sessions.expires_at`
- `user_sessions.revoked_at`

Reglas de auth relevantes:

- `auth_provider = GOOGLE` en MVP.
- `auth_subject` guarda el `sub` del ID token validado por Google.
- El backend debe exigir `email_verified = true` antes de crear o autenticar un user.
- `users.id` es el identificador interno estable de Turnero.
- La sesion se guarda hasheada en `user_sessions`; el token plano vive solo en la cookie del navegador.

## Flujo recomendado

### 1. Frontend inicia Google Sign-In

El frontend usa Google Identity Services.

Para MVP, alcanza con obtener un ID token y enviarlo al backend por HTTPS.

Request sugerido:

```http
POST /api/v1/auth/google
Content-Type: application/json

{
  "id_token": "eyJ..."
}
```

Post-MVP, si Turnero necesita acceder a APIs de Google en nombre del usuario, se puede evaluar authorization code flow. Para el login admin inicial, el ID token alcanza porque solo se necesita identidad.

### 2. Backend valida el ID token

El backend debe validar:

- Firma del token usando claves publicas de Google o libreria oficial.
- `aud` coincide con el Google Client ID de Turnero.
- `iss` es Google.
- `exp` no vencio.
- `sub` existe.
- `email_verified = true`.

Turnero no envia emails de verificacion en MVP. La verificacion del email se delega en Google y el backend solo confia en cuentas cuyo ID token indique `email_verified = true`.

Claims usados:

```text
sub -> users.auth_subject
email -> users.email
name -> users.name
picture -> users.avatar_url
```

No usar:

- Google access token como sesion interna.
- ID token de Google para autorizar endpoints admin.
- `email` como identificador principal si existe `sub`.

### 3. Backend resuelve user local

Busqueda:

```text
auth_provider = GOOGLE
auth_subject = google.sub
```

Si existe:

- Actualizar datos blandos si corresponde: `name`, `email`, `avatar_url`.
- Validar que tenga `business_id`.
- Validar que tenga rol permitido para admin.

Si no existe:

- Crear o dejar preparado `users` segun provisioning controlado.
- En MVP no hay registro publico self-service.
- Si no hay `business_id`, responder error de usuario sin negocio asignado.

### 4. Backend crea sesion Turnero

Generar token aleatorio fuerte:

```text
session_token = base64url(random 32 bytes or more)
session_token_hash = sha256(session_token)
```

Persistir:

```text
user_id
session_token_hash
created_at
expires_at
last_seen_at
ip_address
user_agent
```

Duracion sugerida MVP:

```text
expires_at = now + 7 days
```

Esto puede ajustarse despues segun politica de seguridad.

### 5. Backend devuelve cookie segura

Header sugerido:

```http
Set-Cookie: __Host-turnero_session=<session_token>; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=604800
```

Reglas:

- `HttpOnly`: JavaScript no puede leer la cookie.
- `Secure`: solo via HTTPS.
- `SameSite=Lax`: reduce riesgo CSRF en navegacion normal.
- `Path=/`: cookie disponible para la app.
- Prefijo `__Host-`: exige `Secure`, `Path=/` y sin `Domain`, lo que reduce configuraciones inseguras.

En desarrollo local puede hacer falta una configuracion especial porque `Secure` requiere HTTPS. Esa excepcion debe quedar limitada al perfil local.

### 6. Frontend consulta sesion actual

Endpoint sugerido:

```http
GET /api/v1/auth/me
```

Response sugerida:

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

El frontend no necesita leer la cookie. El navegador la envia automaticamente.

### 7. Backend valida requests admin

En cada endpoint admin:

1. Leer cookie `__Host-turnero_session`.
2. Hashear token recibido.
3. Buscar `user_sessions.session_token_hash`.
4. Validar que exista.
5. Validar `expires_at > now`.
6. Validar `revoked_at is null`.
7. Cargar `users`.
8. Autorizar por `users.business_id` y `users.role`.
9. Actualizar `last_seen_at` con throttling razonable para no escribir en DB en cada request si no hace falta.

## Logout

Endpoint sugerido:

```http
POST /api/v1/auth/logout
```

Comportamiento:

- Leer cookie.
- Buscar sesion activa.
- Completar `revoked_at`.
- Devolver cookie expirada.

Header sugerido:

```http
Set-Cookie: __Host-turnero_session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
```

## Errores esperados

### Google token invalido

```http
401 Unauthorized
```

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "No pudimos validar tu cuenta de Google"
}
```

### Usuario sin negocio asignado

```http
403 Forbidden
```

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Tu usuario todavia no tiene un negocio asignado"
}
```

### Sesion vencida o revocada

```http
401 Unauthorized
```

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Tu sesion vencio. Inicia sesion nuevamente"
}
```

## Seguridad

Reglas MVP:

- Usar HTTPS fuera de desarrollo local.
- No guardar tokens en `localStorage`.
- No guardar tokens de sesion planos en DB.
- No loguear ID tokens, session tokens ni cookies.
- Configurar CORS con origins explicitos.
- Usar cookies `HttpOnly`, `Secure`, `SameSite`.
- Validar CSRF si se usan cookies para requests mutantes desde un frontend separado o si `SameSite` no alcanza para la arquitectura final.
- Mantener secretos y Google Client ID/Client Secret fuera del repo.
- Revocar sesiones en logout.
- Limpiar sesiones vencidas con job periodico.

## Variables de entorno sugeridas

```text
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
TURNERO_SESSION_COOKIE_NAME=__Host-turnero_session
TURNERO_SESSION_TTL_DAYS=7
TURNERO_ALLOWED_ORIGINS=http://localhost:3000
```

`GOOGLE_CLIENT_SECRET` solo es necesario si se usa un flujo con intercambio server-side. Si el MVP usa solo ID token emitido por Google Identity Services, el backend necesita principalmente validar contra `GOOGLE_CLIENT_ID`.

## Componentes backend sugeridos

```text
AuthController
GoogleIdentityService
UserProvisioningService
SessionService
CurrentUserResolver
AuthFilter
```

Responsabilidades:

- `AuthController`: endpoints `/auth/google`, `/auth/me`, `/auth/logout`.
- `GoogleIdentityService`: valida ID token y devuelve identidad externa normalizada.
- `UserProvisioningService`: busca/crea user local y valida business/role.
- `SessionService`: crea, valida y revoca sesiones.
- `CurrentUserResolver`: expone el usuario autenticado a services/controllers.
- `AuthFilter`: valida cookie en endpoints protegidos.

## Identidad externa normalizada

Modelo conceptual:

```text
ExternalIdentity
- provider
- subject
- email
- email_verified
- name
- avatar_url
```

Hoy lo produce Google.

Post-MVP podria producirlo Auth0.

El resto del sistema deberia trabajar con `users.id`, `users.business_id` y `users.role`, no con detalles propios del proveedor.

## Migracion futura a Auth0 u otra plataforma

La migracion deberia afectar principalmente:

- `GoogleIdentityService`, que se reemplazaria o complementaria por un validador Auth0/OIDC.
- El valor de `users.auth_provider`.
- El mapeo de `auth_subject`.

El dominio operativo no deberia cambiar:

- Customers.
- Staff members.
- Appointments.
- Service offerings.
- Business settings.

Riesgo principal:

Si Auth0 emite un `sub` distinto al `sub` directo de Google, habra que mapear identidades. Para eso se puede:

- Migrar por email verificado con revision controlada.
- Agregar post-MVP una tabla `user_identities`.
- Mantener ambos proveedores asociados al mismo `users.id`.

## Referencias

- Google OpenID Connect: https://developers.google.com/identity/openid-connect/openid-connect
- Google backend auth con ID token: https://developers.google.com/identity/sign-in/web/backend-auth
- Google authorization code model: https://developers.google.com/identity/oauth2/web/guides/use-code-model
