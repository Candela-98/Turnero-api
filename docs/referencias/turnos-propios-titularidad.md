# Turnos propios y titularidad explícita — TURN-121

La identidad autenticada tiene un user_id global. Customer contiene la ficha del negocio.
La titularidad de cada turno se guarda en appointments.owner_user_id.
El listado exige conjuntamente el negocio del slug y ese titular explícito.

## Migración y datos existentes

V1006__appointment_customer_ownership.sql se agrega después de V1005 de TURN-120.
Se verificó la disponibilidad de V1006 en la base de TURN-120, origin/develop y las referencias
locales antes de crear el archivo. La base de trabajo es el commit 7c75d9a de TURN-120.

Todos los turnos anteriores reciben owner_user_id=NULL. No hay backfill por ficha, email,
teléfono, source, rol o sesión. Los caminos actuales de reserva invitada y alta admin también
mantienen NULL. Vincular Customer durante un login conserva ese resultado.

fk_appointments_owner_user referencia users(id). fk_appointments_owner_profile referencia
customers(business_id,id,user_id), cuya clave única se agrega en la misma migración.
Con MATCH SIMPLE, los turnos sin titular continúan admitidos. Con titular informado, PostgreSQL
exige que ficha, identidad y negocio correspondan. La titularidad impide desvincular esa ficha
o reasignar el turno a una ficha incompatible sin resolver previamente la operación explícita.

El índice parcial idx_appointments_customer_owner sirve al filtro por negocio y titular y al
orden predeterminado. El esquema conserva los turnos y referencias históricos existentes.

## Contrato de consulta

GET /api/v1/customer/businesses/{slug}/appointments?page=0&size=50&sort=starts_at,desc

Requiere la cookie cliente configurada en TURN-120 y una sesión de ámbito CUSTOMER.
Una identidad OWNER con sesión CUSTOMER puede consultar su propia ficha del negocio.

| Parámetro | Contrato |
| --- | --- |
| page | Desde 0, default 0 |
| size | Entre 1 y 100, default 50 |
| sort | starts_at,asc o starts_at,desc, default descendente |
| Desempate | id ASC para ambos sentidos |

Los parámetros repetidos, vacíos, inválidos o ajenos a ese conjunto reciben 400 VALIDATION_ERROR.
La identidad y el negocio se obtienen del contexto autenticado.
El listado incluye todas las fechas y estados con titularidad explícita, con paginación.

~~~json
{
  "data": [
    {
      "id": 123,
      "service_offering": { "id": 4, "name": "Corte" },
      "staff_member": { "id": 8, "name": "Profesional" },
      "starts_at": "2026-11-25T15:00:00-03:00",
      "ends_at": "2026-11-25T15:30:00-03:00",
      "status": "CONFIRMED"
    }
  ],
  "page": {
    "number": 0,
    "size": 50,
    "total_elements": 1,
    "total_pages": 1
  }
}
~~~

Se reutiliza CustomerPageResponseDto.PageInfo y las convenciones de límite, offset y orden
de la consulta administrativa. CustomerAppointmentReadQuery valida sólo los parámetros del
listado cliente. El COUNT y la página usan el mismo filtro en una transacción de sólo lectura
REPEATABLE_READ. El offset se calcula como long.

El DTO cliente proyecta únicamente los campos del ejemplo. Omite contactos, notas del cliente,
notas internas, motivos de cancelación/bloqueo, precios, tokens e IDs de ficha/titular.
Servicio y profesional se unen por ID y business_id. Se conservan referencias retiradas
lógicamente. Para una referencia histórica inconsistente con el negocio, id y name se devuelven
como NULL. Los nombres reflejan los registros conservados del catálogo.
starts_at y ends_at usan el offset de la zona IANA del negocio, igual que la lectura existente.

| Resultado | Respuesta |
| --- | --- |
| Sesión válida sin turnos propios | 200 con data vacía y totales en cero |
| Página posterior al final | 200 con data vacía y total de turnos propios |
| Cookie ausente, inválida, vencida, revocada o ámbito incorrecto | 401 |
| Sesión de otro negocio o negocio inactivo | 403 |
| Slug inexistente con sesión presente | 404 |
| Query inválida | 400 |

Las respuestas exitosas usan Cache-Control: no-store. El BFF deberá admitir esta ruta GET
y reenviar la cookie cliente al integrar la vista de Mis turnos.

## Validación compartida con TURN-120

CustomerSessionCookie extrae la cookie cliente con las mismas reglas y mensajes anteriores.
CustomerSessionResolver comparte la comprobación de scope CUSTOMER, vigencia, revocación,
customer_id, user_id y negocio del slug.

auth/me y el listado exigen negocio ACTIVE. Logout conserva la posibilidad de revocar en un
negocio inactivo, el 204 idempotente y la expiración de cookie ante una sesión ya inválida.
Login conserva la verificación externa de Google antes del bloqueo de escritura de Customer.
La extracción no cambia el camino de resolución o aprovisionamiento durante ese login.

El listado sólo lee. El bloqueo por negocio de TURN-120 continúa reservado a sus escrituras.

## Ediciones y convivencia con admin

AppointmentMapper ignora ownerUserId al crear desde el DTO admin. Las ediciones sobre la entidad
existente conservan ese campo. Un intento de cambiar customerId en un turno con titularidad
recibe 409 INVALID_STATE_TRANSITION antes de modificar campos. La misma ficha sigue siendo
válida en una edición. Los turnos con titular NULL conservan la reasignación administrativa.

La FK protege también las escrituras SQL incompatibles. Los cambios de horario, servicio,
profesional, notas o estado no adjudican ni transfieren titularidad.

## Contrato de escritura para TURN-122 / B3

TURN-122 implementará POST /api/v1/customer/businesses/{slug}/appointments.
Su servicio debe resolver primero CustomerSessionResolver.requireActive y obtener exclusivamente
de ese contexto business.id, customer.id y session.userId.

En la misma transacción que valida bloqueo, disponibilidad y referencias, construirá el turno
con business_id y customer_id del contexto y owner_user_id=session.userId.
El cuerpo no debe elegir esos identificadores. Las validaciones de reserva se reutilizarán sobre
la ficha resuelta, evitando el camino invitado que resuelve contactos del body.

La creación del turno y de su titular deben confirmar o revertirse juntas. owner_user_id no
se modifica durante el login, recuperación de sesión ni edición de contactos.
source mantiene sus valores existentes y no participa en la autorización de lectura.

La prueba de que el 201 autenticado aparece en este GET corresponde a TURN-122.
En TURN-121, las pruebas preparan turnos con titularidad explícita desde los fixtures.
Los turnos heredados e invitados permanecen fuera de Mis turnos incluso al compartir ficha.

## Comprobaciones

CustomerAppointmentReadPostgresIT prueba vacío, paginación, desempates, fechas/estados,
dos identidades, dos negocios, scopes, expiración, logout, conflictos de reasignación y rollback,
privacidad, referencias históricas y exclusión de invitados vinculados posteriormente.
FlywayMigrationTest verifica desde V1005 la conservación del contenido anterior, las sesiones
legacy y la ausencia de titularidad tanto antes como después de vincular fichas.
Las regresiones HTTP de TURN-120 verifican la extracción compartida de sesión.


## Validación automatizada del 20/11/2026

- Primera corrida focalizada con 101 aprobadas, cero fallas/errores y cero omisiones.
- Suite completa con 864 aprobadas, cero fallas/errores y cinco omisiones optativas de
  MvpQueryPlanPostgresIT. Total registrado de 869 casos.
- CustomerAppointmentReadPostgresIT terminó con 37 casos aprobados, incluyendo los cuatro
  cambios de estado y la inserción concurrente entre COUNT y página bajo REPEATABLE_READ.
- FlywayMigrationTest terminó con diez casos aprobados. Las regresiones de TURN-120,
  lectura administrativa y edición de turnos quedaron incluidas en la suite completa.

Se ejecutó Java 21, Gradle 8.14.2 y PostgreSQL 16 con Testcontainers.
JAVA_TOOL_OPTIONS=-XX:+UseSerialGC se aplicó al proceso local, como en TURN-120.
Los resultados de la ejecución están en build/reports/tests/test/index.html.
El recorrido de creación autenticada y consulta posterior corresponde a TURN-122.
