# Guía manual de TURN-120 con Google real

Esta guía prueba C:\Turnero\WorkSpace\turnero-api-turn-120. La ejecución manual con una cuenta
Google real queda pendiente del operador. Las pruebas HTTP automatizadas usan PostgreSQL 16
con Testcontainers y sustituyen la verificación Google. El verificador tiene su propia cobertura.

Validación manual informada por el usuario al cierre del 19/11/2026

- Perfil público verificado.
- Servicios verificados.
- Disponibilidad verificada.
- Recorrido interactivo con Google real pendiente, incluida la reserva invitada seguida del login
  y la comprobación manual del mismo customer_id con user_id vinculado.

El vínculo por email verificado se describe en [la decisión de TURN-120](customer-google-session.md).
Una ficha histórica puede contener un email ingresado por un tercero. La vinculación automática
asume correcto ese contacto cuando existe una única coincidencia con el email verificado de Google.

## Levantar el worktree

Usar una base desechable propia de QA. El Compose versionado fija container_name a turnero-postgres.
Estos comandos usan otro contenedor, base, puerto HTTP y nombres de cookies.

En PowerShell, crear una vez el contenedor y comprobar que PostgreSQL esté listo.
Si ya existe el contenedor de QA, usar docker start turn120-manual-postgres.

~~~powershell
Set-Location C:\Turnero\WorkSpace\turnero-api-turn-120
docker run --detach --name turn120-manual-postgres --publish 127.0.0.1:55432:5432 --env POSTGRES_USER=turnero --env POSTGRES_PASSWORD=turnero --env POSTGRES_DB=turnero120_manual postgres:16-alpine
docker exec turn120-manual-postgres pg_isready -U turnero -d turnero120_manual
~~~

El perfil dev carga migraciones y el seed Barber Studio. Make importa .env automáticamente.
Gradle invocado desde PowerShell recibe las variables exportadas en esa terminal.

~~~powershell
$env:JAVA_HOME='C:\Users\outlet\.jdks\corretto-21.0.8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE='dev'
$env:DB_HOST='localhost'
$env:DB_PORT='55432'
$env:DB_NAME='turnero120_manual'
$env:DB_USERNAME='turnero'
$env:DB_PASSWORD='turnero'
$env:GOOGLE_CLIENT_ID=Read-Host 'OAuth Client ID web de Google'
$env:JAVA_TOOL_OPTIONS='-XX:+UseSerialGC'
.\gradlew.bat bootRun --args='--spring.profiles.active=dev --server.port=8082 --auth.session.secure=false --auth.session.cookie-name=turn120_admin_session --auth.session.customer-cookie-name=turn120_customer_session --spring.jpa.show-sql=false --logging.level.org.hibernate.orm.jdbc.bind=OFF'
~~~

En esta máquina existe además un Gradle instalado y una caché preparados durante QA.
Como alternativa al wrapper, con las mismas variables exportadas, ejecutar lo siguiente.

~~~powershell
$env:GRADLE_USER_HOME='C:\Turnero\.gradle-turn120'
$turn120Gradle='C:\Users\outlet\.gradle\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2\bin\gradle.bat'
& $turn120Gradle --offline bootRun --args='--spring.profiles.active=dev --server.port=8082 --auth.session.secure=false --auth.session.cookie-name=turn120_admin_session --auth.session.customer-cookie-name=turn120_customer_session --spring.jpa.show-sql=false --logging.level.org.hibernate.orm.jdbc.bind=OFF'
~~~

Ejecutar una variante de bootRun. Abrir http://localhost:8082/swagger-ui/index.html.
El proceso debe aplicar V1005 y validar el esquema. V1005 completa scope=ADMIN en sesiones
anteriores y elimina el default. El despliegue del esquema requiere actualizar los escritores
que todavía omitan scope. Esta guía parte de una base nueva.

En Google Cloud, usar un OAuth Client ID web con http://localhost:8082 entre los Authorized
JavaScript origins y habilitar la cuenta de prueba en el consentimiento cuando corresponda.
El Client ID debe coincidir con GOOGLE_CLIENT_ID.
El flujo usa la biblioteca oficial Google Identity Services y su callback JavaScript.
[Botón oficial](https://developers.google.com/identity/gsi/web/guides/display-button),
[referencia del callback](https://developers.google.com/identity/gsi/web/reference/js-reference).

## Preparar el segundo negocio y las consultas

En otra terminal, entrar únicamente a la base de QA.

~~~powershell
docker exec -it turn120-manual-postgres psql -U turnero -d turnero120_manual
~~~

Dentro de psql, preparar variables y un segundo negocio ACTIVE. La reserva de esta guía se
hace en el negocio del seed. El negocio B sirve para comprobar otra ficha de la misma identidad.

~~~sql
\set ON_ERROR_STOP on
\pset pager off
\set slug_a barber-studio
\set slug_b turn-120-business-b
SELECT id AS business_a FROM businesses WHERE slug = :'slug_a' \gset

INSERT INTO businesses (name, slug, timezone, status, onboarding_status, created_at, updated_at)
SELECT 'TURN-120 B', :'slug_b', timezone, 'ACTIVE', 'READY', LOCALTIMESTAMP, LOCALTIMESTAMP
FROM businesses
WHERE id = :business_a
  AND NOT EXISTS (SELECT 1 FROM businesses WHERE slug = :'slug_b');

SELECT id AS business_b FROM businesses WHERE slug = :'slug_b' \gset
SELECT id, slug, status FROM businesses WHERE id IN (:business_a, :business_b);
SELECT version, success FROM flyway_schema_history WHERE version = '1005';
SELECT column_default, is_nullable FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'user_sessions' AND column_name = 'scope';
~~~

Esperar V1005 exitosa, scope obligatorio y column_default NULL.

## Preparar el botón de Google en el navegador

En Swagger de localhost:8082, abrir DevTools y ejecutar este bloque en Console, una vez por
recarga. Coloca un botón temporal y ofrece llamadas same-origin a la API. El Client ID es público.
El ID token pasa directamente del callback al login y permanece fuera de las variables de QA.

request filtra campos token/credential antes de imprimir o devolver respuestas, incluido
cancel_token de la reserva. Para evidencia usar respuestas filtradas y SQL por IDs.
Evitar exportar HAR, copiar payloads de login, Cookie/Set-Cookie o volcar el callback de Google.

~~~javascript
await (async () => {
  window.turn120 = {
    slugA: "barber-studio",
    slugB: "turn-120-business-b",
    target: { kind: "customer", slug: "barber-studio" },
    clientPath(slug, action) {
      return "/api/v1/customer/businesses/" + encodeURIComponent(slug) + "/auth/" + action;
    },
    async request(path, method = "GET", body) {
      const response = await fetch(path, {
        method,
        credentials: "same-origin",
        headers: body === undefined ? {} : { "Content-Type": "application/json" },
        body: body === undefined ? undefined : JSON.stringify(body)
      });
      const text = await response.text();
      const safeBody = text ? JSON.parse(text, (key, value) =>
        /token|credential/i.test(key) ? undefined : value) : null;
      const result = { status: response.status, body: safeBody };
      console.log(result);
      return result;
    },
    me(slug) {
      return this.request(this.clientPath(slug, "me"));
    },
    logout(slug) {
      return this.request(this.clientPath(slug, "logout"), "POST");
    },
    dayAfter(days) {
      const date = new Date();
      date.setDate(date.getDate() + days);
      return new Intl.DateTimeFormat("en-CA", {
        timeZone: "America/Argentina/Buenos_Aires",
        year: "numeric", month: "2-digit", day: "2-digit"
      }).format(date);
    }
  };
  const qa = window.turn120;
  const clientId = prompt("Mismo GOOGLE_CLIENT_ID del backend");
  await new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.onload = resolve;
    script.onerror = () => reject(new Error("No se pudo cargar Google Identity Services"));
    document.head.append(script);
  });
  const panel = document.createElement("div");
  panel.style.padding = "20px";
  document.body.prepend(panel);
  google.accounts.id.initialize({
    client_id: clientId,
    auto_select: false,
    callback: async response => {
      const target = { ...qa.target };
      const path = target.kind === "admin"
        ? "/api/v1/auth/google" : qa.clientPath(target.slug, "google");
      try {
        await qa.request(path, "POST", { id_token: response.credential });
      } catch {
        console.error("No se pudo completar el login. Revisar el estado de la API.");
      }
    }
  });
  google.accounts.id.renderButton(panel, { theme: "outline", size: "large" });
})();
~~~

Este procedimiento ejercita Google real y las cookies del backend. La integración browser/BFF
del frontend requiere su propia verificación. El botón permite probar las rutas nuevas aunque
la allowlist del BFF todavía no las incluya.

## Caso 1. Reservar como invitada y vincular la misma ficha

Hacer este caso antes de pulsar Google. Usar el email de la cuenta real y un teléfono de QA que
no coincida con otras fichas del negocio. En Console, consultar servicio y disponibilidad.

~~~javascript
turn120.email = prompt("Email exacto de la cuenta Google de QA");
turn120.phone = prompt("Teléfono de QA sin otra ficha en esta base");
turn120.catalog = (await turn120.request(
  "/api/v1/public/businesses/" + turn120.slugA + "/services")).body;
turn120.service = turn120.catalog.services[0];
turn120.slots = (await turn120.request(
  "/api/v1/public/businesses/" + turn120.slugA + "/availability?" +
  new URLSearchParams({
    from: turn120.dayAfter(1),
    to: turn120.dayAfter(6),
    service_offering_id: String(turn120.service.id),
    staff_member_id: "any"
  }))).body;
~~~

Confirmar que slots contiene un hueco. Si está vacío, revisar horarios y catálogo del seed en
Swagger antes de reservar. Usar el horario y un profesional devueltos por la API.

~~~javascript
turn120.slot = turn120.slots[0];
turn120.reservation = await turn120.request(
  "/api/v1/public/businesses/" + turn120.slugA + "/appointments", "POST", {
    service_offering_id: turn120.service.id,
    staff_member_id: String(turn120.slot.available_staff_member_ids[0]),
    starts_at: turn120.slot.starts_at,
    customer: { name: "Invitada QA", email: turn120.email, phone_number: turn120.phone }
  });
~~~

Esperar 201 y registrar appointment_id. En psql, cargar ese número y obtener la ficha.

~~~sql
\prompt 'appointment_id devuelto por la API: ' appointment_qa
SELECT customer_id AS customer_a FROM appointments WHERE id = :appointment_qa \gset
SELECT id, business_id, user_id, name, status, public_booking_blocked, created_at, updated_at
FROM customers WHERE id = :customer_a;
SELECT id, customer_id, business_id, status FROM appointments WHERE id = :appointment_qa;
~~~

Antes del login, user_id debe ser NULL. Registrar ID, estado y timestamps.
Seleccionar el destino y pulsar Google en la página, eligiendo esa cuenta.

~~~javascript
turn120.target = { kind: "customer", slug: turn120.slugA };
~~~

Esperar 200 con customer.id igual a customer_a. Comprobar recuperación.

~~~javascript
await turn120.me(turn120.slugA);
~~~

Repetir las consultas anteriores. user_id debe estar informado. El ID, nombre, estado, timestamps
y customer_id de la reserva deben conservarse. La cookie de esta QA es turn120_customer_session,
HttpOnly, Path=/, Secure=false para HTTP local.

Para comprobar archivo y bloqueo en la base desechable, actualizar esa ficha, volver a pulsar
Google y repetir auth/me. Ambos deben recuperar el mismo ID y conservar flags.

~~~sql
UPDATE customers SET status = 'INACTIVE', public_booking_blocked = true,
    public_booking_block_reason = 'QA manual TURN-120'
WHERE id = :customer_a;
~~~

Esperar INACTIVE y public_booking_blocked=true. Las reservas públicas conservan su rechazo para
esa ficha. Restaurar las condiciones de QA si se continúa reservando.

## Caso 2. Mismo Google en dos negocios

Seleccionar B y pulsar Google con la misma cuenta.

~~~javascript
turn120.target = { kind: "customer", slug: turn120.slugB };
~~~

Esperar 200 con otra ficha y business.slug=turn-120-business-b. En psql, comprobar el vínculo.

~~~sql
SELECT user_id AS qa_user FROM customers WHERE id = :customer_a \gset
SELECT id AS customer_b FROM customers
WHERE business_id = :business_b AND user_id = :qa_user \gset

SELECT id, business_id, user_id, status FROM customers
WHERE id IN (:customer_a, :customer_b) ORDER BY business_id;
SELECT count(*) AS fichas, count(DISTINCT user_id) AS identidades
FROM customers WHERE id IN (:customer_a, :customer_b);
SELECT id, auth_provider, role, business_id FROM users WHERE id = :qa_user;
~~~

Esperar dos IDs de Customer, dos negocios y una identidad. El OWNER ficticio del seed es otro
registro de prueba y queda fuera de este conteo.

~~~javascript
await turn120.me(turn120.slugB); // 200
await turn120.me(turn120.slugA); // 403
await turn120.logout(turn120.slugA); // 403, conserva sesión B
await turn120.me(turn120.slugB); // 200
~~~

La cookie cliente es única por host. Otro login cliente selecciona ese negocio en el navegador.
Para volver a A, seleccionar A y pulsar Google otra vez. Debe recuperar customer_a.
Para continuar el caso siguiente, dejar seleccionada la sesión cliente B.

## Caso 3. Convivencia OWNER y cliente con el mismo Google

En la base desechable, habilitar acceso OWNER a la identidad ya verificada por el backend.
Este UPDATE es parte del fixture manual de QA. Evita copiar el sub o el ID token de Google.

~~~sql
UPDATE users SET role = 'OWNER', business_id = :business_a, updated_at = LOCALTIMESTAMP
WHERE id = :qa_user
RETURNING id, role, business_id;
~~~

La ficha A puede seguir archivada/bloqueada. La identidad conserva también su ficha B.
Seleccionar el destino admin y pulsar el mismo botón Google.

~~~javascript
turn120.target = { kind: "admin" };
~~~

Esperar 200 con role=OWNER y business.slug=barber-studio. Comprobar las dos sesiones.

~~~javascript
await turn120.request("/api/v1/auth/me"); // 200, OWNER de A
await turn120.request("/api/v1/business"); // 200, negocio A
await turn120.me(turn120.slugB); // 200, ficha B
~~~

La cookie admin de QA es turn120_admin_session. En psql verificar scopes por IDs.

~~~sql
SELECT id, user_id, customer_id, scope, created_at, expires_at, revoked_at
FROM user_sessions WHERE user_id = :qa_user ORDER BY id;
~~~

Esperar ADMIN con customer_id NULL y CUSTOMER con la ficha correspondiente.
Los dos ámbitos pueden usar el mismo user_id.

## Caso 4. Recuperación, logout y rechazo admin

Recargar Swagger y volver a ejecutar el bloque del botón, sin pulsarlo.
Las cookies sobreviven a la recarga. auth/me admin y cliente B deben responder 200.

Revocar la sesión admin y comprobar que el cliente sigue vigente.

~~~javascript
await turn120.request("/api/v1/auth/logout", "POST"); // 204
await turn120.request("/api/v1/business"); // 401, sólo queda cookie cliente
await turn120.request("/api/v1/auth/me"); // 401
await turn120.me(turn120.slugB); // 200
~~~

Volver a seleccionar destino admin y pulsar Google. Esperar 200 y luego revocar cliente.

~~~javascript
turn120.target = { kind: "admin" };
~~~

~~~javascript
await turn120.logout(turn120.slugB); // 204
await turn120.me(turn120.slugB); // 401
await turn120.request("/api/v1/auth/me"); // 200, OWNER
await turn120.logout(turn120.slugB); // 204 idempotente
~~~

Repetir la consulta de sesiones. La sesión CUSTOMER revocada debe tener revoked_at.
Para probar expiración, iniciar sesión cliente B otra vez y vencer esa sesión nueva en QA.

~~~javascript
turn120.target = { kind: "customer", slug: turn120.slugB };
// Pulsar Google y esperar 200 antes de ejecutar el UPDATE siguiente.
~~~

~~~sql
UPDATE user_sessions SET expires_at = LOCALTIMESTAMP - INTERVAL '1 minute'
WHERE id = (
    SELECT max(id) FROM user_sessions
    WHERE user_id = :qa_user AND customer_id = :customer_b AND scope = 'CUSTOMER'
);
~~~

auth/me cliente B debe devolver 401 y logout 204. auth/me admin debe seguir devolviendo 200.
Las pruebas automáticas cubren también tokens de un ámbito enviados bajo el nombre de cookie
del otro. La prueba manual de rechazo admin usa únicamente cookie cliente.

Las proyecciones SQL de esta guía omiten session_token_hash, auth_subject y contactos personales.
Guardar como evidencia status HTTP, IDs de negocio/ficha/reserva, scopes y fechas.

## Registro de ejecución y fallo nativo

La suite completa previa a esta revisión terminó con 821 pruebas aprobadas, cero fallas/errores
y cinco omisiones de MvpQueryPlanPostgresIT, cuya auditoría es optativa.
La revisión posterior aprobó 63 pruebas focalizadas, cero fallas/errores y cero omisiones.
Incluye 21 casos PostgreSQL de sesión cliente y tres casos sobre el orden de verificación Google.
El informe corriente está en build/reports/tests/test/index.html y se regenera con cada ejecución.

El 18/11/2026, Corretto 21.0.8+9 en Windows interrumpió una corrida con
EXCEPTION_ACCESS_VIOLATION (0xc0000005) en jvm.dll durante el hilo G1 Conc#1.
El diagnóstico conservado está en build/reports/hs_err_pid12668.log, fuera del código versionado.
Una corrida posterior con JAVA_TOOL_OPTIONS=-XX:+UseSerialGC terminó correctamente.
La causa nativa de G1 queda sin determinar. SerialGC se aplicó a los comandos locales de prueba.

Para reproducir las pruebas focalizadas de esta revisión en esta máquina, con Java y caché
definidos antes, ejecutar lo siguiente.

~~~powershell
$env:JAVA_TOOL_OPTIONS='-XX:+UseSerialGC'
& $turn120Gradle --offline --no-daemon test --tests '*CustomerAuthServiceTest' --tests '*CustomerAuthPostgresIT' --tests '*CustomerServiceImplTest' --tests '*CustomerControllerIT' --tests '*CustomerArchiveBlockPostgresIT'
~~~

Al terminar, Ctrl+C detiene la API y docker stop turn120-manual-postgres detiene el contenedor QA.
