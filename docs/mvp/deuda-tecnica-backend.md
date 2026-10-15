# Deuda Tecnica Backend MVP

## Proposito

Registrar brechas tecnicas conocidas que no bloquean el avance actual, pero deben validarse y resolverse antes de cerrar la integracion MVP con frontend.

El caso de appointments documentado abajo es un ejemplo detectado durante una revision puntual. No representa una lista exhaustiva: antes de cerrar el MVP se debe revisar cada flujo backend + frontend contra sus contratos, reglas de negocio y datos persistidos para identificar inconsistencias equivalentes.

Este documento no reemplaza los contratos ni el backlog de Jira. Cada item que se decida implementar debe tener su ticket asociado.

## Auth - convergencia previa a TURN-69

Jira: `TURN-88` para contrato, autorizacion, cookie y logout; `TURN-89` para la proteccion de business-hours; `TURN-62`, `TURN-110` y `TURN-116` para boundary HTTP, cleanup y logs sensibles.

### Contrato HTTP

El tracking registra como completada la convergencia de contrato de TURN-88: request de Google, body de login, estructura de `/auth/me`, cookie y roles. Los atributos reales de cookie, defaults por perfil y configuración externa viven en [el contrato canónico](api-contracts-mvp.md#cookie-por-ambiente); no deben interpretarse como una parametrización uniforme mediante AUTH_SESSION_*.

### Protección de business hours

El tracking registra TURN-89 como completado: `/api/v1/business-hours/**` está incluido en AdminAuthInterceptor y tiene cobertura de sesión, rol y scoping. Esta autenticación backend no reemplaza la validación de procedencia pendiente en el BFF.

### Logout idempotente

En la branch revisada para TURN-62, AuthController ya expira la cookie y responde `204` aunque falte o la sesión esté vencida/revocada. No se registra como brecha de código pendiente; esta observación no acredita un merge ni cierre en Jira. La validación de procedencia de logout en el BFF sigue pendiente en la subtarea indicada abajo.

### Aprovisionamiento de desarrollo

El seed usa un `auth_subject` ficticio. El procedimiento repetible de aprovisionamiento local está documentado en [Google Auth MVP](auth/google-mvp.md#aprovisionamiento-local) y su entrega se registra en el tracking. No es una brecha de la decisión HTTP de TURN-62.

### Frontera HTTP y mitigacion pendiente en el BFF

La decision canonica de TURN-62 vive en [Seguridad y scoping](api-contracts-mvp.md#frontera-http-bff-cors-y-csrf-turn-62). El PR backend documenta la frontera y mantiene CORS sin configurar; no implementa la mitigacion del BFF.

Brecha abierta: el BFF es responsable de validar procedencia/CSRF en mutaciones browser, pero la mitigacion explicita todavia NO esta implementada. Requiere una **subtarea separada de frontend/BFF pendiente de crear/vincular**, sin ID confirmado. Debe implementar el mecanismo y las pruebas definidos por la decision canonica, incluidos login/logout, mutaciones admin, creacion/cancelacion publica y verificacion de cookies en local/HTTPS. Booking publico debe conservar el BFF cuando se integre.

No considerar completo el hardening de TURN-32 ni la mitigacion operativa por cerrar la decision documental TURN-62. Los defaults versionados de cookie no acreditan la configuracion efectiva del despliegue.

### Ciclo de vida de sesiones

Trabajo programado:

- `TURN-110`: limpiar sesiones vencidas o revocadas periodicamente.
- `TURN-62`: cerrar la decision BFF, CORS y CSRF sin habilitar acceso cross-origin por defecto.
- `TURN-116`: verificar que cookies, tokens y credenciales no lleguen a logs.

Deuda aun no programada, fuera de esas subtareas:

- actualizar `last_seen_at` con throttling;
- definir limites o revocacion global de sesiones por usuario.

## Appointments - campos controlados por backend

Jira: `TURN-90` para lectura de agenda, `TURN-91` como contenedor de `TURN-105`/`TURN-109` para invariantes de escritura y `TURN-92` para availability admin. `TURN-113` valida la invariante final bajo concurrencia real.

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

El resumen operativo requerido por el dashboard se implementara bajo `TURN-93`: `TURN-106` define el contrato, `TURN-108` entrega el resumen basico y `TURN-107` incorpora metricas y alertas.

Durante las pruebas finales, revisar cada flujo MVP de punta a punta. Para cada uno, comprobar que:

- El request no permite modificar campos que deben controlar el backend.
- El backend aplica las reglas de negocio y persiste datos consistentes.
- La respuesta tiene los datos y el formato que necesita el frontend.
- Los estados, permisos y transiciones coinciden con el contrato.
- Las validaciones de seguridad, scoping y concurrencia se mantienen al escribir.

Registrar cada hallazgo en este documento y crear el ticket Jira correspondiente cuando se programe su resolucion.

## Hardening operativo programado

`TURN-32` es la historia contenedora. El detalle ejecutable y sus criterios viven en Jira:

| Brecha | Jira |
| --- | --- |
| Boundary BFF, CORS y CSRF | TURN-62 |
| Cleanup de sesiones | TURN-110 |
| Cleanup de tokens publicos | TURN-111 |
| Rate limiting publico | TURN-112 |
| Concurrencia real de reservas | TURN-113 |
| Indices de consultas MVP | TURN-114 |
| Datos sensibles en logs | TURN-116 |
| Runbook operativo | TURN-115 |

Este inventario enlaza responsables y no reemplaza alcance, dependencias, criterios ni estado de Jira.
