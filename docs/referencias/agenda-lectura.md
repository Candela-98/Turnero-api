# Agenda administrativa — TURN-90 / TURN-70

Implementación coordinada de lectura, sobre `develop` backend y `dev` frontend actualizados el 2026-10-20. PR #85 excluido. Crear dos PRs tras aprobación manual, integrar backend primero. No se consulta CI ni se integra automáticamente.

## Diseño

`AppointmentReadQuery` valida fechas, rangos, filtros, orden y paginación fuera del controller. `AppointmentReadService` obtiene negocio/zona y mantiene snapshot por petición. `AppointmentReadRepository` ejecuta count y proyección SQL con tres LEFT JOIN limitados al mismo negocio. No usa repositorios por turno. `AppointmentReadDto` sirve para colección y detalle; no altera DTOs de escritura.

La política temporal, ejemplos y contrato completo están en [api-contracts-mvp.md](../mvp/api-contracts-mvp.md#appointments-admin). `BusinessLocalTimeRange` separa rangos de instantes en ventanas locales constantes; elige primer offset en repeticiones y avanza horas inexistentes, igual que la serialización Java. Esto permite usar el índice temporal sin aplicar funciones a cada fila. No cambia timestamps históricos. El orden usa la hora local persistida y desempata por ID.

## Medición reproducible

Ejecutar con Java 21 y Docker: `make test`. Con Colima el Makefile configura su socket. `AppointmentReadPostgresIT` inicia PostgreSQL 16 descartable, ejecuta migraciones, genera 100.000 appointments en dos negocios y 365 fechas, hace ANALYZE y mide día, rango filtrado y orden descendente mediante `EXPLAIN (ANALYZE, BUFFERS)` de las consultas exactas del repositorio. Compara tamaños 1, 50 y 100: dos statements JDBC por página; la búsqueda de zona del servicio agrega uno fijo.

El resultado íntegro se genera en `build/reports/agenda-performance.txt`; una ejecución revisada se conserva en [agenda-performance.txt](../qa/turn-90/agenda-performance.txt). Reutiliza `(business_id, starts_at)` y los índices existentes; no se agrega migración ni índice. La medición usa pocas referencias distintas y caché local: sirve para verificar el plan y descartar N+1, no representa un SLO productivo. Los conteos no crecen en cantidad de statements al aumentar filas; el costo del count y offsets profundos sí puede crecer con la cardinalidad.

## Prueba manual local

Para nuevas cargas compartidas, usar la [guía del script por API](seed-agenda-local.md): `make seed-local-appointments BUSINESS_ID=1` simula 106 turnos desde hoy y dos días más; `AGENDA_APPLY=1` aplica si hay capacidad completa. Reutiliza catálogo/disponibilidad y las validaciones del POST. No duplica ni reprograma los históricos. La carga autenticada de esta nueva herramienta permanece pendiente; sus 15 pruebas usan API simulada.

El siguiente escenario SQL se conserva solo como reproducción histórica de casos extremos en una DB descartable; no es la rutina de carga recomendada:

1. Iniciar backend local como indica [entorno-local.md](entorno-local.md); mantener una sesión OWNER del negocio de prueba.
2. Desde `Turnero-api`, preparar solo la DB local con `psql "$DATABASE_URL" -v business_id=ID_DEL_NEGOCIO -v agenda_date=2026-10-20 -f scripts/seed-local-agenda.sql`. Revisar que DATABASE_URL sea local antes de ejecutarlo. El script es transaccional y agrega 106 turnos ficticios, un cliente archivado y referencias inactivas/eliminadas; no carga el benchmark. Cada ejecución agrega otro conjunto, así que ejecutarlo una vez en una DB de desarrollo descartable.
3. Configurar `TURNERO_API_BASE_URL=http://localhost:8080` en frontend y ejecutar `npm run dev`. Abrir `/agenda?date=2026-10-20` con el mismo negocio.
4. Verificar 106 turnos (todos los estados), varios a las 07:15 y 14:00, profesional histórico a las 23:30 con final 00:30 (+1 día), filtros provisionales, pills, limpiar, anterior/siguiente/Hoy/selector. El cliente archivado sigue visible. La disponibilidad no forma parte de esta pantalla.
5. Revisar 1440×900, 390×844 y 412×915: foco, teclado, fechas, columnas con scroll interno y último turno por encima de la navegación mobile. Probar acceso denegado y errores usando los mocks E2E, sin alterar datos reales.

Las acciones demo de crear/editar/confirmar/mensajería/detalle, Nuevo turno del shell, Semana, disponibilidad ficticia y paneles de métricas se retiran temporalmente. Las tareas de escritura, disponibilidad y TURN-73 conservan su alcance.

## Entrega

Implementación local y QA automática se registran en tracking. El usuario aprobó la agenda y autorizó crear los PRs el 2026-10-23; la integración sigue pendiente. No cargar datos de performance en la base local del usuario. Los E2E prueban el BFF en unitarios del handler real y la UI con respuestas simuladas; la prueba local autenticada contra ambos procesos es parte de la revisión manual.
