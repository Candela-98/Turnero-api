# Sesión de cliente con Google — TURN-120

## Identidad y alcance

User representa una identidad global por (auth_provider, auth_subject), protegida por uq_users_auth.
users.business_id conserva el contexto administrativo de las cuentas internas.
Una identidad creada desde login cliente tiene role=CUSTOMER y business_id=NULL.
Una identidad OWNER conserva su fila, rol y negocio al iniciar sesión como cliente.

Customer contiene la ficha propia de cada negocio. uq_customers_business_user garantiza una ficha
por (business_id, user_id). Las fichas invitadas conservan user_id=NULL y pueden existir sin login.
Los vínculos anteriores se conservan, incluso si el negocio de la ficha difiere del contexto
administrativo del usuario. Ese vínculo representa acceso cliente a otro negocio.

V1005 completa las sesiones anteriores con scope=ADMIN y elimina el default dentro de la misma
migración. Todos los escritores nuevos deben indicar ámbito. El despliegue requiere retirar los
escritores antiguos antes de servir tráfico con el esquema nuevo.

Las sesiones CUSTOMER guardan user_id y customer_id. La FK compuesta hacia customers(id,user_id)
exige que la ficha corresponda a la identidad de la sesión. El negocio se resuelve desde esa ficha.
ADMIN conserva el vínculo por user_id y exige rol OWNER. Una sesión CUSTOMER puede pertenecer
a una identidad OWNER y sigue requiriendo ámbito ADMIN para entrar en rutas administrativas.

## Resolución de ficha

POST /api/v1/customer/businesses/{slug}/auth/google recibe {"id_token":"..."}.
Exige un negocio ACTIVE y la verificación Google existente, incluido email_verified=true.
Primero busca la ficha por negocio y usuario global. Ese vínculo prevalece en accesos posteriores,
incluido un cambio de email en Google o en la ficha.

Cuando falta el vínculo, busca todas las coincidencias del negocio con lower(trim(email)).
Incluye fichas INACTIVE y public_booking_blocked. Una única ficha invitada se vincula conservando
ID, nombre, contactos, notas, timestamps, estado, bloqueos y reservas. Varias coincidencias reciben
409 CUSTOMER_IDENTITY_AMBIGUOUS. Una ficha vinculada a otra identidad recibe
409 CUSTOMER_IDENTITY_CONFLICT. Ambos errores revierten la transacción y no emiten cookie.
Sin coincidencias, crea la identidad global si hace falta y una ficha ACTIVE del negocio.
GoogleIdentityDto actualmente contiene sub y email. Para una ficha nueva se usa el nombre de User
si existe, o el email verificado truncado al máximo admitido por users.name.

La vinculación automática por una coincidencia única de email verificado es una decisión de
producto de TURN-120. Los contactos históricos pueden haber sido ingresados por terceros.
Una coincidencia no acredita quién creó la ficha ni verifica la exactitud del email histórico.
La decisión puede conceder acceso a una ficha cuyo contacto fue cargado incorrectamente.
La respuesta de auth expone datos de la ficha, estado y bloqueo público. Las notas internas y
el motivo privado del bloqueo quedan fuera del DTO de sesión.

El login conserva INACTIVE y los bloqueos. El bloqueo sigue impidiendo reservas públicas según
las reglas existentes. El login y auth/me permiten recuperar esa ficha sin cambiar esas condiciones.

## HTTP y cookies

GET /api/v1/customer/businesses/{slug}/auth/me recupera la ficha desde la sesión CUSTOMER
y valida el negocio del slug. Una sesión válida de otro negocio recibe 403. Sesiones ausentes,
inválidas, vencidas, revocadas o de ámbito ADMIN reciben 401.

POST /api/v1/customer/businesses/{slug}/auth/logout revoca únicamente una sesión CUSTOMER
del negocio solicitado y expira la cookie cliente. Responde 204 también si la cookie falta o ya
no tiene una sesión válida. Una sesión válida de otro negocio recibe 403 y conserva cookie y sesión.
El logout permite revocar sesiones del negocio aunque su estado haya pasado a INACTIVE.
El logout admin conserva su comportamiento idempotente y valida ámbito ADMIN antes de revocar.

La cookie cliente por defecto es turnero_customer_session en HTTP local y
__Host-turnero_customer_session cuando auth.session.secure=true. Puede configurarse mediante
auth.session.customer-cookie-name y debe diferir de auth.session.cookie-name. Es HttpOnly,
Path=/, sin Domain, con SameSite, Secure y TTL compartidos con la configuración de sesiones existente.
El navegador conserva una cookie cliente por host. Otro login cliente reemplaza esa cookie y
selecciona el nuevo negocio. Las sesiones anteriores siguen vigentes hasta revocación o expiración.
La cookie administrativa es independiente.

Las respuestas de cliente tienen Cache-Control: no-store. El BFF debe permitir las tres rutas,
reenviar las cookies y Set-Cookie, y aplicar la frontera de procedencia/CSRF del contrato HTTP
existente. La integración BFF no está incluida en este cambio backend.

## Atomicidad y concurrencia

Login cliente, creación administrativa, actualización/archivo de fichas y resolución de invitados
de reserva pública toman un bloqueo pesimista sobre la fila Business antes de bloquear o escribir
Customer. El bloqueo se mantiene hasta el commit de la operación externa, incluida una reserva.
Esto también coordina el caso donde todavía no existe ninguna fila Customer que bloquear.

google.verify(idToken) y la comprobación de email verificado ocurren antes de adquirir el
bloqueo Business. La transacción del login empieza al entrar al servicio y puede mantener una
conexión durante la validación externa. La espera de Google transcurre antes de tomar locks de
resolución de fichas. auth/me, logout y las lecturas admin tampoco toman este bloqueo.

Dentro del bloqueo se consulta el vínculo estable y luego el email. El alta global usa
INSERT ... ON CONFLICT (auth_provider,auth_subject) DO NOTHING y una lectura posterior bajo
READ COMMITTED. Permite que dos primeros logins en negocios distintos compartan una identidad.
El alta de ficha, el vínculo y la sesión forman una transacción. Fallar posteriormente revierte todo.

Las altas administrativas que coincidan por email con una ficha vinculada del mismo negocio
reciben 409 CUSTOMER_IDENTITY_CONFLICT. Esto evita crear una segunda ficha cuando un login
se adelanta a un alta admin simultánea. Si el alta admin termina primero, el login vincula esa ficha.
Las altas con contactos compartidos entre fichas invitadas mantienen el comportamiento previo.

Los cambios de email admin conservan la posibilidad de compartir contactos entre fichas.
Se serializan con el login y conservan user_id. El email de contacto no transfiere el vínculo.
Una identidad ya vinculada se recupera por user_id aunque el email se repita o cambie.
Una identidad sin vínculo recibe el error de ambigüedad/conflicto correspondiente al resolver el contacto. Las coincidencias entre fichas invitadas preexistentes siguen
siendo posibles y se resuelven mediante el error de ambigüedad durante el login.
La serialización por negocio reduce concurrencia entre escrituras de fichas de ese negocio.
Las escrituras externas por SQL deben respetar este protocolo. Las claves únicas siguen protegiendo
la identidad global y el vínculo de ficha por negocio.


La [guía manual con Google real](guia-manual-turn-120-google.md) incluye configuración local,
consultas por IDs, casos de sesión y el registro de la ejecución con SerialGC.


## Revisión previa al commit del 18/11/2026

El bloqueo sobre Business cubre la ausencia de una ficha. SELECT FOR UPDATE sobre Customer
sólo protege filas existentes. La clave (business_id,user_id) admite invitados con user_id NULL,
así que tampoco coordina una búsqueda de contacto vacía con el alta simultánea de un invitado.
La fila Business ya existe y ofrece un punto común para todas esas operaciones.

| Operación | Alcance del bloqueo |
| --- | --- |
| Login cliente | Después de Google, hasta el commit de vínculo/alta y sesión |
| Alta admin de Customer | Antes de resolver coincidencias vinculadas, hasta el commit del alta |
| Cambio admin de contacto, estado o bloqueo | Antes del lock de Customer y su refresh, hasta el commit |
| Archivo admin | Mismo orden Business y luego Customer, hasta el commit |
| Resolución de invitados en reserva pública | Antes de consultar contactos, hasta el commit de reserva y token público |

auth/me, logout y lecturas admin conservan sus validaciones de sesión y negocio sin adquirir
este bloqueo de resolución. Negocios distintos toman filas Business distintas. Una identidad
nueva compartida entre dos negocios puede esperar además por uq_users_auth.

La serialización reduce paralelismo entre escrituras de fichas del mismo negocio. Se eligió
porque la reserva invitada resuelve por email o teléfono y los contactos pueden cambiar desde
admin. El mismo protocolo coordina ambos contactos sin agregar una tabla de locks ni restricciones
de unicidad sobre emails históricos compartidos.

La revisión retiró el rechazo general de cambios de email admin. Los cambios de contacto
mantienen user_id y la recuperación estable. El alta admin que duplicaría por email una ficha
ya vinculada conserva 409 para cubrir la carrera donde el login termina antes del alta.
Cuando admin crea primero, el login vincula su ficha. Las fichas invitadas con contactos repetidos
siguen admitidas y el primer login aplica sus errores de ambigüedad/conflicto.

También se restauró desde origin/develop la cadena UTF-8 del error de longitud del motivo de
bloqueo, que había quedado dañada durante las ediciones de la etapa anterior.

La ejecución de esta revisión aprobó 63 pruebas focalizadas con SerialGC, cero fallas/errores
y cero omisiones. Incluye 21 casos PostgreSQL de CustomerAuthPostgresIT y tres pruebas del orden
de validación de Google. La suite completa anterior había aprobado 821 pruebas con cinco
omisiones optativas. La prueba interactiva con Google real queda para la guía manual.
