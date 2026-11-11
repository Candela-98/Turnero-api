# TURN-112: rate limiting del booking público

Estado: implementación local y validación. **No considerar TURN-112 cerrada** hasta
resolver los criterios de despliegue pendientes de este documento.

## Alcance y políticas

Solo se limitan las operaciones reales de PublicBookingController y
PublicCancellationController. READ incluye GET y HEAD de profile, services,
availability y consulta de cancelación. WRITE incluye POST de appointments y
cancelación. OPTIONS, métodos sin mapping y endpoints administrativos no consumen
cuota. Las peticiones admitidas consumen cuota aunque luego fallen por validación
o reglas de negocio; no se ofrece idempotencia.

Cada solicitud usa un bucket global por IP y familia READ/WRITE. Las rutas con
businessSlug usan además otro por IP + negocio + familia. El slug conserva el
case usado por findBySlug y se representa internamente con SHA-256 para acotar
el tamaño de la clave. Ese identificador no se publica ni registra. Las
cancelaciones solo usan el global: nunca se toma el token para formar claves.

| Política | Capacidad inicial | Recarga greedy |
| --- | --- | --- |
| Global READ | 60 | 60 permisos/minuto |
| Global WRITE | 10 | 10 permisos/minuto |
| Negocio READ | 30 | 30 permisos/minuto |
| Negocio WRITE | 5 | 5 permisos/minuto |

Son valores provisionales y ajustables. Una capacidad completa permite una
ráfaga; la recarga es continua, no una ventana fija. La navegación, consultas de
disponibilidad y usuarios que comparten NAT deben validarse antes de producción.

La configuración vive en public-booking.rate-limit de application.yml.
enabled=false evita acceso al registro y consumo en ambos alcances. Los bloques
read/write y business.read/business.write exponen capacity, refill-tokens y
refill-period, todos con variables de entorno PUBLIC_BOOKING_RATE_LIMIT_*.
Ejemplo: PUBLIC_BOOKING_RATE_LIMIT_BUSINESS_WRITE_CAPACITY=8.
La configuración se valida al iniciar y no admite grupos ausentes, valores
no positivos o duraciones que no caben en nanosegundos. Cada una de las cuatro
políticas se construye con Bucket4j durante la validación: una tasa incompatible
(por ejemplo, más de un permiso por nanosegundo) impide iniciar la aplicación. Los cambios de política
requieren reiniciar la instancia; no hay recarga dinámica de buckets existentes.

## Almacenamiento y concurrencia

Caffeine conserva referencias fuertes, sin maximumSize ni expiración automática.
La expulsión por tamaño podía eliminar un bucket agotado y recrearlo lleno.
Ahora el registro realiza admisión explícita con max-buckets (default 10000).
Cada petición necesita una entrada global y, en booking, otra por negocio.

Un único monitor protege estimación, admisión, consumo de ambos buckets y limpieza.
No salen referencias mutables a Bucket. Si falla cualquier cuota no se consume
ninguna. Tampoco se consume si no hay espacio suficiente para ambas claves.
Todos los buckets faltantes se construyen antes de publicar entradas o consumir
permisos. Un fallo de construcción del segundo alcance no consume cuota global
ni deja publicada una entrada parcial. Esto evita carreras de comprobación y
consumo, así como reembolsos inseguros.
El monitor serializa el trabajo del limitador; no incluye HTTP, BD ni reglas de
negocio. Su rendimiento debe validarse con la carga esperada.

Solo se elimina una entrada que lleva idle-retention sin uso (default 10m) y cuyo
bucket ya tiene TODOS los permisos. El reloj monotónico es compartido por recarga
y retención. Por eso ni una retención menor al período de recarga repone cuota
prematuramente. La limpieza es oportunista, al admitir claves nuevas, como máximo
una vez por segundo para acotar CPU. La memoria queda acotada aun sin tráfico.

Si el registro se llena, los clientes ya registrados conservan sus cuotas y se
rechazan nuevas claves con 503 / RATE_LIMIT_STORAGE_FULL, Cache-Control: no-store.
No se inventa Retry-After: no conocemos cuándo habrá espacio. Esta política
favorece preservar la protección sobre la disponibilidad para clientes nuevos.
Un ataque con muchas identidades puede saturarlo: observar saturaciones con
métricas agregadas y ajustar capacidad/retención según memoria y tráfico real.

Variables adicionales:
- PUBLIC_BOOKING_RATE_LIMIT_MAX_BUCKETS (mínimo 2).
- PUBLIC_BOOKING_RATE_LIMIT_IDLE_RETENTION (duración positiva).

## Errores y datos sensibles

429 conserva ErrorResponse: status, error, code=RATE_LIMIT_EXCEEDED, message,
details, path, timestamp. Retry-After usa segundos redondeados hacia arriba y
Cache-Control: no-store. RequestIdFilter sigue agregando X-Request-Id.

El filtro solo acepta un único UUID canónico de 36 caracteres como identificador
entrante, normalizado a minúsculas. Ante ausencia, valores arbitrarios, múltiples
headers o valores extensos, genera un UUID del servidor. Ese mismo valor se usa
en respuesta y MDC; el MDC se limpia aun ante excepciones. No se usa para autenticar
ni para identificar clientes del limitador.

ErrorResponse.getPath aplica ErrorPathSanitizer a todos los handlers. Las
cancelaciones se representan como /api/v1/public/cancellations/{token}, incluso
con escapes, matrix params, segmentos adicionales y variantes con . o ..
(también codificados). Se inspeccionan las formas previas y posteriores a
normalizar; la ruta de cancelación devuelta es canónica, sin prefijos de contexto.
Las rutas ambiguas que no se pueden reconocer con seguridad se sustituyen por
/{redacted}; nunca se reenvían como dato diagnóstico. La decodificación anidada
se acota a cinco pasadas. SanitizedErrorAttributes
protege también el path del fallback servlet /error, conservando su contrato.
Los códigos HTTP, códigos y mensajes de errores existentes se conservan.

El logger genérico del advice registra solo el tipo de excepción; omite mensaje
y stack trace, que podrían contener URI o credenciales. El limitador no registra
IP, slugs, claves, hashes, tokens ni cookies. No etiquetar métricas por esos datos.
Esto no acredita la sanitización de access logs del BFF, balanceador o servidor
externo; sigue requiriendo revisión de TURN-116.

## Identificación de IP y pendientes de despliegue

ClientIpResolver utiliza getRemoteAddr(). application.yml fija
server.forward-headers-strategy=none. No se leen X-Forwarded-For, Forwarded ni
X-Real-IP y no se configura una lista de proxies confiables para AWS.

**Pendiente antes de aceptar el despliegue:** decidir el recorrido real
browser/BFF/proxy/backend, el aislamiento de red del backend y la transmisión
verificada de IP. Actualmente, detrás de un BFF todos sus clientes pueden
compartir la cuota de la IP del BFF. No habilitar procesamiento indiscriminado
de forwarded headers; el límite de confianza deberá borrar/sobrescribir headers
entrantes. Verificar la configuración efectiva, incluidos overrides de entorno.

También queda pendiente probar que el BFF propaga 429, Retry-After y no-store,
y que sus logs y métricas no contienen URLs con tokens.

## Límites inevitables y criterios de aceptación

- Solo una JVM: reinicios, despliegues y fallos pierden el estado y reponen cuotas.
  No se persiste ni se coordina con otra réplica. Un despliegue con dos instancias
  no mantiene el límite global declarado. Revisar antes de escalar.
- La IP no identifica personas: NAT puede producir falsos positivos y múltiples
  IP o rotación IPv6 pueden eludir cuotas. No es protección completa contra DDoS.
- Probar carga y tráfico normal antes de fijar cuotas y máximo de claves.
- Verificar la frontera real de IP y propagación HTTP del BFF.
- Auditar los logs externos con tokens de prueba, sin registrar tokens reales.
- Mantener pendiente la mitigación BFF/CSRF de TURN-62: rate limiting no la sustituye.

## Verificación local (2026-10-21)

Compilación Java 21: correcta. Última selección disponible: 596 tests en 56 clases,
sin fallos, errores ni tests omitidos dentro de esa selección. Son 46 casos más
que la selección anterior de 550. Se ejecutó Gradle en modo offline con compileJava
y test, seleccionando los *Test salvo FlywayMigrationTest, los *ControllerIT y
ServiceOfferingTimestampsIT. No equivale a la suite completa con PostgreSQL.

Las regresiones cubren normalización y ambigüedad de rutas, un dispatch real
ERROR de Tomcat hacia /error, filtro -> MDC -> logs (incluidos errores), las cuatro
políticas incompatibles con Bucket4j, ausencia de consumo/publicación parcial
ante fallos de construcción, saturación HTTP real con 503 y cuotas existentes
con 429/Retry-After/no-store. También se cubren concurrencia entre negocios y
READ/WRITE, además del tope real de 10000 entradas.

Docker no está disponible en localhost:2375, confirmado fuera del sandbox.
Quedaron sin ejecutar FlywayMigrationTest, BusinessHoursPostgresIT,
CustomerArchiveBlockPostgresIT, CustomerListPostgresIT, CustomerWritePostgresIT,
SessionCleanupPostgresIT, StaffHoursPostgresIT, StaffMemberCreationPostgresIT,
StaffServicesPostgresIT y StaffStatusPostgresIT. No se modificó Docker/AWS ni
se añadieron dependencias. Siguen pendientes las verificaciones de despliegue.

## Archivos de esta revisión

Producción y configuración (rutas relativas al repositorio):
- src/main/java/com/turnero/api/config/PublicRateLimitProperties.java
- src/main/java/com/turnero/api/config/WebMvcConfig.java
- src/main/java/com/turnero/api/config/RequestIdFilter.java
- src/main/java/com/turnero/api/ratelimit/PublicRateLimitBucketStore.java
- src/main/java/com/turnero/api/ratelimit/PublicRateLimiter.java
- src/main/java/com/turnero/api/ratelimit/PublicRateLimitInterceptor.java
- src/main/java/com/turnero/api/exception/ErrorResponse.java
- src/main/java/com/turnero/api/exception/GlobalExceptionHandler.java
- src/main/java/com/turnero/api/exception/ErrorPathSanitizer.java (nuevo)
- src/main/java/com/turnero/api/exception/SanitizedErrorAttributes.java (nuevo)
- src/main/java/com/turnero/api/exception/PublicRateLimitStorageFullException.java (nuevo)
- src/main/resources/application.yml

Tests:
- src/test/java/com/turnero/api/config/RequestIdFilterTest.java
- src/test/java/com/turnero/api/exception/PublicErrorFallbackHttpTest.java (nuevo)
- src/test/java/com/turnero/api/ratelimit/PublicRateLimitSaturationHttpTest.java (nuevo)
- src/test/java/com/turnero/api/controller/PublicBookingControllerTest.java
- src/test/java/com/turnero/api/controller/PublicCancellationControllerTest.java
- src/test/java/com/turnero/api/ratelimit/PublicRateLimiterTest.java (nuevo)
- src/test/java/com/turnero/api/ratelimit/PublicRateLimitHttpTest.java (nuevo)
- src/test/java/com/turnero/api/config/PublicRateLimitPropertiesTest.java (nuevo)
- src/test/java/com/turnero/api/exception/ErrorPathSanitizerTest.java (nuevo)
- src/test/java/com/turnero/api/exception/SanitizedErrorAttributesTest.java (nuevo)

Documentación:
- docs/mvp/rate-limiting-publico.md (nuevo)
- docs/mvp/api-contracts-mvp.md
- docs/mvp/deuda-tecnica-backend.md

Los cambios previos de build.gradle, ClientIpResolver y
PublicRateLimitExceededException se conservaron sin editarlos en esta revisión.