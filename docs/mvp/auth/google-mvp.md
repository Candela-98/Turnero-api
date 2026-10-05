# Turnero API — Google Auth MVP

Actualizado: 2026-10-05

## Propósito y ownership

Este documento explica la arquitectura, implementación y operación de Google Auth en Turnero. El wire contract canónico —payloads, respuestas, códigos y cookie por ambiente— vive únicamente en `../api-contracts-mvp.md`, sección `Auth/session`. La decisión HTTP/BFF/CORS/CSRF vive en [Seguridad y scoping](../api-contracts-mvp.md#frontera-http-bff-cors-y-csrf-turn-62).

El estado de entrega vive en `../tracking-implementacion-mvp.md` y las mejoras pendientes en `../deuda-tecnica-backend.md`. Este archivo no duplica esos backlogs.

## Decisión MVP

```text
Google Identity Services -> Turnero valida identidad -> user aprovisionado
-> sesión opaca propia -> cookie HTTP-only -> autorización por business y rol
```

- Google prueba identidad; no funciona como sesión interna.
- El backend usa el ID token una sola vez y nunca lo usa para autorizar APIs admin.
- Turnero genera un token aleatorio, persiste sólo su hash SHA-256 y entrega el token plano en una cookie HTTP-only.
- No existe registro público ni aprovisionamiento automático.
- Para el MVP administrativo, el usuario debe pertenecer a un business y tener rol `OWNER`.

## Flujo funcional

1. El frontend obtiene un ID token mediante Google Identity Services.
2. Lo envía al BFF same-origin bajo `/api/backend`; Next.js lo intercambia en `POST /api/v1/auth/google` según el contrato canónico.
3. El backend valida firma, audiencia, emisor, expiración y `email_verified`.
4. Resuelve al usuario por `auth_provider = GOOGLE` y `auth_subject = sub`.
5. Verifica business y rol permitidos.
6. Crea `user_sessions`, responde el contexto de usuario/business y setea la cookie.
7. El frontend restaura la sesión a través del BFF, que consulta `GET /api/v1/auth/me`.
8. El logout pasa por el BFF hacia `POST /api/v1/auth/logout`; Spring revoca la sesión si corresponde y expira la cookie de forma idempotente.

## Sesión y autorización

La sesión contiene un token opaco de 32 bytes codificado en Base64 URL-safe. En base de datos sólo se guarda su hash junto con usuario, creación, expiración, revocación, IP y user-agent.

Reglas MVP:

- TTL predeterminado de siete días, configurable mediante auth.session.ttl-days según el contrato canónico; no hay refresh ni expiración deslizante.
- Pueden coexistir varias sesiones del mismo usuario.
- `AuthenticatedUserContext` vive por request.
- `AuthenticatedCurrentBusinessContext` obtiene el business desde el usuario autenticado.
- Los endpoints admin no aceptan `business_id` elegido por el cliente.
- `401` representa ausencia o invalidez de sesión; `403`, identidad válida sin autorización suficiente.

## Cookie por ambiente

Los atributos, defaults versionados y overrides externos se documentan únicamente en [Cookie por ambiente](../api-contracts-mvp.md#cookie-por-ambiente). En particular, desa hereda Secure=false salvo configuración externa; no debe suponerse que HTTPS lo activa automáticamente.

## Configuración de Google

Backend y frontend deben usar el mismo OAuth 2.0 Client ID web.

```text
backend:  GOOGLE_CLIENT_ID=<client-id-web>
frontend: NEXT_PUBLIC_GOOGLE_CLIENT_ID=<mismo-client-id-web>
```

En Google Cloud, el cliente web debe incluir el origen real del frontend; para desarrollo, por ejemplo `http://localhost:3000`. El MVP usa ID token y no necesita `GOOGLE_CLIENT_SECRET`.

No registrar ID tokens, cookies ni tokens de sesión en logs, errores, analytics o fixtures.

## Aprovisionamiento local

El seed local crea un OWNER con `auth_subject = google-demo-owner-mateo-ruiz`, que es un valor ficticio. Una cuenta Google real no puede iniciar sesión hasta que su `sub` sea aprovisionado.

Para una prueba local controlada:

1. Obtener el `sub` y email verificados de la cuenta de desarrollo sin persistir el ID token.
2. Ejecutar `GOOGLE_OWNER_SUB='<sub>' GOOGLE_OWNER_EMAIL='<email>' make provision-local-owner`.
3. El comando sólo acepta el perfil `dev` y una base en `localhost` o `127.0.0.1`; actualiza exclusivamente el OWNER demo local.
4. No versionar identificadores personales ni reemplazar el seed compartido con datos reales.
5. Para ambientes compartidos, usar un mecanismo de aprovisionamiento administrativo; no SQL manual ni registro público.

## Integración con Next.js

El admin implementado usa un BFF/proxy same-origin de Next; booking público debe conservar esa frontera al integrar la UI. El recorrido y el inventario de consumidores están en [la decisión HTTP canónica](../api-contracts-mvp.md#frontera-http-bff-cors-y-csrf-turn-62).

El BFF debe:

- usar una URL backend server-only;
- reenviar método, body, `Content-Type`, `Accept` y `Cookie`;
- reenviar al navegador el header `Set-Cookie` de login/logout;
- conservar status y formato de error de la API;
- no cachear auth ni mutaciones;
- limitar el proxy a rutas `/api/v1` conocidas, sin aceptar destinos arbitrarios.

El frontend hidrata sesión con `/auth/me`, limpia estado local ante `401` y muestra acceso denegado ante `403`. El logout local debe completarse aunque la sesión backend ya haya vencido.

Spring mantiene CORS sin configurar. El BFF es responsable de validar procedencia/CSRF en las mutaciones, pero esa mitigación todavía NO está implementada: requiere una subtarea frontend/BFF pendiente de crear/vincular. La política completa y su matriz de mutaciones viven en el contrato canónico enlazado arriba.

## Implementación actual y convergencia pendiente

PRs #61 y #62 implementaron `AuthController`, validación Google, sesiones, contexto autenticado, logout y protección admin. El tracking registra la convergencia de contrato de TURN-88. El hardening posterior se divide en TURN-62 para la decisión de frontera BFF/CORS/CSRF, una subtarea frontend/BFF pendiente de crear/vincular para la mitigación, TURN-110 para cleanup de sesiones y TURN-116 para logs sensibles. El contrato vive en `../api-contracts-mvp.md`; el inventario y la prioridad se mantienen en `../deuda-tecnica-backend.md` y `../tracking-implementacion-mvp.md`.

## Componentes implementados

- `AuthController`: login, sesión actual y logout.
- `GoogleIdentityService`: validación del ID token.
- `AuthService`: usuario local, business y contexto de respuesta.
- `SessionService`: creación, hash, validación y revocación.
- `AdminAuthInterceptor`: autenticación y autorización admin.
- `AuthenticatedUserContext` y `AuthenticatedCurrentBusinessContext`: identidad por request.

## Referencias

- Google OpenID Connect: https://developers.google.com/identity/openid-connect/openid-connect
- Google backend auth con ID token: https://developers.google.com/identity/sign-in/web/backend-auth
