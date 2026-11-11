# Medición de planes de consultas MVP

## Resumen ejecutivo

La auditoría de [TURN-114](https://turnero-app.atlassian.net/browse/TURN-114)
comparó consultas reales sobre PostgreSQL 16.14 y el esquema Flyway existente.
La evidencia respalda conservar los 43 índices funcionales actuales. Los dos
índices de laboratorio se evaluaron únicamente en contenedores descartables.
**TURN-107/dashboard sigue pendiente y queda fuera de la cobertura medida.**

Las cifras describen fixtures sintéticos con semilla fija, mantenimiento
controlado y caché calentada. Se separó el calentamiento de las cinco muestras
por escenario. Las medianas corresponden a ejecución SQL. La distribución, la
frecuencia de uso, la concurrencia y la latencia de producción siguen sin
medirse. Los costos de escritura excluyen commit y mantenimiento posterior.

### Resultados y decisiones

| Consulta | Índice utilizado en los escenarios citados | Resultado principal | Decisión |
| --- | --- | --- | --- |
| Q01 · Agenda | `idx_appointments_business_starts_at` para el acceso a turnos del negocio, con índices de joins según el plan | Con 3.301 coincidencias en 31 días, la página inicial y la profunda devolvieron 50 filas cada una. Los buffers hit pasaron de 410 a 855 y la mediana de 1,243 a 25,577 ms. | Mantener el esquema. Si el costo se confirma con uso real, evaluar la paginación profunda y su `OFFSET`. |
| Q02 · Conteo | `idx_appointments_business_starts_at` | En el conteo de esa ventana, el acceso index-only registró 12 buffers hit y cero `Heap Fetches` tras vacuum. | Mantener el índice. Interpretar el resultado junto con la visibilidad del heap. |
| Q04 · Conflictos | `idx_appointments_staff_starts_at` y `idx_appointments_business_starts_at` | Con más historial, el mismo profesional pasó de 2.427 a 10.427 turnos materializados. La evaluación del solapamiento en Java queda fuera de EXPLAIN. | Mantener el esquema. Si se retoma, evaluar cuánto historial debe recuperar la consulta. |
| Q05 · Disponibilidad | `idx_appointments_staff_starts_at` en baseline, `exp_appointments_staff_ends_at` en los pares citados | Con 8.000 turnos históricos adicionales, el candidato redujo las filas revisadas de 10.406 a 200 y los buffers hit de 1.003 a 194. En el control con turnos futuros pasó de 2.406 a 8.200 filas revisadas. | Dejar el candidato condicionado a la mezcla temporal y al costo de escritura. |
| Q11 · Selección de sesiones | `user_sessions_pkey` para el OR en los tres volúmenes principales, parciales `idx_user_sessions_cleanup_expires_at` y `idx_user_sessions_cleanup_revoked_at` para UNION ALL | Con 20.000 sesiones y 50 elegibles, bajó de 313 a 8 buffers hit. Con 5.000 y 1.000 elegibles, subió de 18 a 56. Los IDs y el orden coincidieron. | Conservar la reescritura en laboratorio. El beneficio depende de la densidad y posición de elegibles. |
| Q12 · Borrado de sesiones | `user_sessions_pkey` y parciales de limpieza según el escenario | Lotes de 50 o 500 filas con restauración verificada. | Mantener el esquema y la revalidación al borrar. |
| Q16 · Selección de tokens | `idx_public_tokens_cleanup_expires`, `idx_public_tokens_cleanup_used` y `appointments_pkey` | Con 50 elegibles, mantuvo 14 buffers hit al pasar de 5.000 a 20.000 tokens agregando filas de otros negocios. | Mantener el esquema. El escenario cubre crecimiento fuera del negocio medido. |
| Q17 · Borrado de tokens | PK de tokens y turnos, con parciales de limpieza según el escenario | Lotes de 50 o 500 filas con restauración verificada. | Mantener el esquema y la revalidación al borrar. |
| Q25 · Asociaciones por servicio | Seq Scan en baseline, `exp_staff_service_offerings_service` en el brazo candidato | Los buffers hit bajaron de 13 o 46 a 2–3. El ahorro de mediana fue de 0,062 a 0,971 ms por lectura. | Posponer la migración hasta conocer frecuencia y beneficio acumulado. |

### Candidatos y costo de mantenimiento

- **Q05** · `exp_appointments_staff_ends_at (staff_member_id, ends_at)` comparte
  la primera columna con el índice por inicio, pero resuelve otro rango. El
  beneficio con historial antiguo se invirtió en el control futuro y las
  altas y reprogramaciones agregaron WAL. La evidencia no justifica Flyway.
- **Q25** · `exp_staff_service_offerings_service (service_offering_id)` aporta
  una clave inicial que el UNIQUE actual por profesional y servicio no cubre.
  Ocupó 40–72 KiB y agregó 6.512 bytes de WAL por cada alta de 100 asociaciones
  en el laboratorio. El beneficio absoluto y la frecuencia aún desconocida
  dejan la migración pendiente.
- **Q11** · UNION ALL reutilizó los dos parciales de V3 y añadió un ordenamiento
  global. La alternativa sigue en laboratorio, sin DDL ni cambios productivos.

### Trazabilidad de aceptación

| Criterio de Jira | Estado | Evidencia y validación pendiente |
| --- | --- | --- |
| Cada índice nuevo referencia una consulta concreta. | Cumplido | Los candidatos experimentales se vinculan a Q05 y Q25. No hay índices nuevos en Flyway. |
| Se documentan índices evaluados, agregados y descartados. | Cumplido para lo medido | La matriz identifica índices y decisiones. El catálogo y las corridas de referencia están identificados en el manifiesto de evidencia. Dashboard queda pendiente. |
| Las migraciones son compatibles con el esquema existente. | Cumplido para el cambio entregado | No hay migraciones nuevas. Flyway instaló el esquema en el contenedor; queda pendiente registrar una ejecución final de `FlywayMigrationTest` para las rutas de actualización. |
| No se duplican prefijos cubiertos por índices actuales sin justificación. | Cumplido | Se evaluó la diferencia de claves de los candidatos. Los catálogos de cierre conservaron las definiciones originales. |
| Flyway y consultas críticas se verifican sobre PostgreSQL. | Parcial | PostgreSQL 16.14 cubre las nueve consultas de la matriz. Falta dashboard por TURN-107 y la validación final de Flyway. |

Los tests de repositorio para consultas modificadas que menciona Jira no
aplican a este cambio, porque las consultas productivas permanecen intactas.

## Alcance

Laboratorio opt-in de agenda, conflictos, disponibilidad, limpieza de sesiones
y tokens, y asociaciones por servicio. Usa PostgreSQL 16 en Testcontainers,
Flyway real y repositorios sin modificaciones. El DDL experimental está
restringido al contenedor descartable. Se mide SQL, sin latencia de endpoints.

La única clase nueva es `src/test/java/com/turnero/api/integration/MvpQueryPlanPostgresIT.java`.
Su configuración Spring anidada reutiliza el patrón de los tests de limpieza:
sin servidor web, sin servicios ni schedulers, con Hibernate `validate`.
Una condición JUnit impide ejecutar la clase salvo con
`TURNERO_QUERY_PLAN_AUDIT=true`; no se modifica `build.gradle`.

## Requisitos y ejecución

Java 21, Docker disponible para Testcontainers y acceso a las dependencias y a
la imagen `postgres:16-alpine`. El tag es mutable: cada manifiesto registra el
image ID efectivo y la versión exacta del servidor. Conservar esa imagen para
una repetición estricta; un resultado de otro image ID no es idéntico por definición.

Desde la raíz del repositorio, en PowerShell:

```powershell
.\gradlew.bat compileTestJava
```

Para comprobar que la medición está deshabilitada, con la variable ausente:

```powershell
.\gradlew.bat test --tests "com.turnero.api.integration.MvpQueryPlanPostgresIT" --rerun-tasks
```

Los métodos deben quedar omitidos, sin levantar el contenedor de esta clase.
Para reproducir una fase, usar su filtro por método. El siguiente ejemplo
ejecuta las cuatro variantes de baseline y preserva el valor previo de la variable:

```powershell
$previousAuditFlag = $env:TURNERO_QUERY_PLAN_AUDIT
try {
    $env:TURNERO_QUERY_PLAN_AUDIT = "true"
    .\gradlew.bat test --tests "com.turnero.api.integration.MvpQueryPlanPostgresIT.measureExistingIndexesOnDeterministicDataset" --rerun-tasks
}
finally {
    if ($null -eq $previousAuditFlag) {
        Remove-Item Env:TURNERO_QUERY_PLAN_AUDIT -ErrorAction SilentlyContinue
    } else {
        $env:TURNERO_QUERY_PLAN_AUDIT = $previousAuditFlag
    }
}
```

El build existente también genera JaCoCo al finalizar `test`. La salida de esta
suite está en `build/reports/query-plan/<instante>-<container-id>/<variante>/` y
muestra la ruta absoluta de la ejecución en consola. No ejecutar `clean` antes
de conservar la evidencia. Los directorios de ejecuciones anteriores no se sobrescriben.

## Segunda fase: variantes independientes y mantenimiento controlado

El protocolo `phase2-controlled-vacuum` ejecuta cuatro variantes en orden fijo:

| Variante | Turnos | Asociaciones | Mediciones |
| --- | ---: | ---: | --- |
| `v2-vacuumed` | 50.000 | 1.960 | Los 50 escenarios originales más cuatro combinaciones Q01/Q02 históricas: 324 planes |
| `history-plus-2000` | 52.000 | 1.960 | Q05 de P1, mismas ventanas de 1/7 días y vacía: 18 planes |
| `history-plus-8000` | 58.000 | 1.960 | Q05 de P1, mismas ventanas: 18 planes |
| `associations-expanded` | 50.000 | 7.220 | Q25 de los mismos tres servicios: 18 planes |

Son 378 planes: 63 calentamientos y 315 muestras. No hay límites de tiempo para
aprobar rendimiento; `status.json` registra duración total y por variante, carga,
mantenimiento y tramo de medición/captura. Se mantienen los timeouts de protección
por sentencia. La ejecución recorre sólo estas cuatro variantes, sin multiplicar
todas las combinaciones ni agregar clases o tareas de build.

Antes de cada variante se verifica URL exacta, PostgreSQL 16, contenedor activo y
ausencia de transacción explícita. Sólo entonces se ejecuta `TRUNCATE businesses
RESTART IDENTITY CASCADE` en la base exclusiva del contenedor (nunca se trunca
Flyway). Se limpian los fixtures en memoria y se reconstruye v2 desde la misma
semilla. Las variantes de historial agregan 2.000 u 8.000 respecto de la base:
no se acumulan entre sí. Q25 tampoco hereda las modificaciones de Q05.

El contenedor se inicia con `autovacuum=off` exclusivamente mediante su comando
de arranque; no se usa ALTER SYSTEM ni se toca otro servidor. La configuración
efectiva se comprueba y exporta en manifiesto/settings. Registrar fsync y demás
opciones de escritura permite identificar diferencias respecto de imágenes o
ejecuciones anteriores; no comparar sus tiempos como un ensayo exclusivo de VACUUM.

Con la carga ya confirmada y sin transacción explícita, una conexión JDBC
verificada con autocommit ejecuta `VACUUM (ANALYZE)` de las seis tablas cargadas.
Esa conexión no se usa para medir. El mantenimiento queda fuera de los tiempos
EXPLAIN y del tramo registrado de medición. No hay sleeps para esperar estadísticas:
se solicita su flush y se exportan snapshots; ciertos contadores acumulativos
pueden reflejar variantes previas porque se reutiliza el mismo servidor.

Se registran antes de VACUUM, antes y después de medir: relpages, relallvisible,
reltuples, timestamps y contadores de vacuum/analyze, y estimaciones de tuplas.
Se verifica que todas las páginas cargadas sean all-visible después del VACUUM
y que relpages/relallvisible permanezcan iguales al terminar las lecturas.
Las mediciones siguen siendo de caché calentada: el reset de datos no limpia las
cachés del servidor ni del sistema operativo. ANALYZE sigue teniendo muestreo.

### Comparaciones y controles de equivalencia

- **Q01 histórica:** negocio B2, desde 30 días antes de la referencia hasta el
  día siguiente: 31 días completos dentro del fixture. Se comprueba actividad
  todos los días. ASC, tamaño 50, página cero frente a última página completa
  `(total-50)/50`. Ambas respuestas deben contener 50 registros y coincidir con
  el fixture ordenado. Los nombres comienzan con `historical31-` y se exportan
  fechas, cantidades por día y página en `historical-agenda.json`.
- **Q02:** conserva todos los conteos originales y agrega los de la ventana
  histórica. El conteo no cambia de SQL por página; comparar esas repeticiones
  como tales. Los planes completos conservan Heap Fetches; CSV agrega
  `heap_fetches` (vacío si no hay nodo que lo informe). No se exige Index Only Scan.
- **Q05:** sólo se agregan turnos de P1 anteriores al historial original: entre
  91 y 365 días antes de la referencia, con duración de 30 minutos y mezcla
  exacta de 20% por estado agregado. El generador de extensión usa streams
  independientes derivados de 114; sus primeros 2.000 registros son iguales en
  ambas variantes. Los turnos originales, incluido el de borde, no cambian.
- **Q25:** se completan pares faltantes de servicios locales 3 a 19, sin nuevos
  catálogos. Los servicios de referencia 1/2/20 conservan 20/1/0 asociaciones,
  incluidos sus IDs. Se respetan negocio y UNIQUE. La tabla pasa a 7.220 filas.

`equivalence.json` registra SHA256 calculados sobre filas persistidas, ordenadas:
todos los 50.000 turnos originales, los turnos recientes de los profesionales
1/20/400 y las asociaciones de referencia. Los hashes deben coincidir con la base.
Además se comparan los IDs exactos devueltos por Q05 en 1/7 días para los tres
profesionales en las cuatro variantes. En escenarios compartidos se exige SQL,
bindings y opciones JDBC idénticos a los capturados en la variante base.

Las definiciones de todos los índices y sus flags valid/ready se verifican contra
el catálogo inicial de Flyway antes y después de medir. Tamaños de índices sí
pueden crecer con los datos; eso no es un cambio de definición.

Los resultados de las fases anteriores siguen siendo referencia histórica:
no tienen el mismo control de autovacuum/visibilidad. Una conclusión sobre índices
requiere revisar planes, cardinalidades, descartes, buffers y estabilidad, no
sólo diferencias de tiempo. Trabajo evitable que crezca bajo estas condiciones
puede justificar un futuro ensayo de candidatos; no aprueba un índice productivo.

## Dataset y verificaciones

Semilla Java Random 114; referencia local fija `2026-10-28T12:00`, negocio en
`America/Argentina/Buenos_Aires`. Ningún dato depende del reloj de ejecución.

| Entidad | Cantidad |
| --- | ---: |
| Negocios | 20 |
| Profesionales | 400 (20 por negocio) |
| Servicios | 400 (20 por negocio) |
| Clientes | 4.000 (200 por negocio) |
| Turnos | 50.000 |
| Asociaciones | Calculadas por regla determinista y exportadas |

Cada profesional ofrece un servicio común y entre 1 y 7 adicionales; el primero
de cada negocio ofrece además un servicio exclusivo. El último servicio de cada
negocio no tiene asociaciones. Se respetan pares UNIQUE y todas las FK/CHECK.
Las inserciones usan lotes de 500 dentro de una transacción y las secuencias
de IDs explícitos se avanzan al máximo cargado.

El 40% aproximado de turnos se concentra en los dos primeros negocios. Un cuarto
aproximado se concentra en el primer profesional de cada negocio. Hay 98 fechas
posibles: desde 90 días antes de la referencia hasta 7 después. Se distribuyen
todos los estados y ambas fuentes; turnos de 30 minutos a intervalos de 15.
El turno 50.000 cruza la medianoche de la fecha de referencia y es CONFIRMED.

### Corrección de estados (v2)

La ejecución del 28/10/2026 reveló que negocio y estado dependían ambos de
`id % 5`: los negocios 1/2 tenían sólo estados bloqueantes y los demás sólo
estados no bloqueantes. Esa correlación era accidental y limita las conclusiones
de disponibilidad de aquella evidencia, que se conserva sin sobrescribir.

La revisión `v2-independent-state-cycles` asigna a cada profesional ciclos de los
cinco estados, barajados con un Random independiente derivado de la semilla 114.
Así se conserva el stream original de negocios, profesionales, fechas y clientes,
sin consumir números aleatorios adicionales de ese stream. Cada ciclo completo
contiene PENDING, CONFIRMED, CANCELLED, COMPLETED y NO_SHOW una vez. No pretende
representar proporciones de producción: es una mezcla de laboratorio controlada.
El turno de borde sigue forzado a CONFIRMED.

Se comprueban los conteos persistidos: los 20 negocios y 400 profesionales tienen
los cinco estados; la diferencia máxima entre estados por profesional es dos,
incluyendo el turno de borde. Por negocio, cada estado está dentro de ±21 turnos
del quinto del total (ciclos incompletos de 20 profesionales más el borde).
Los profesionales Q05 1, 20 y 400 conservan sus cargas distintas y deben tener
turnos bloqueantes y no bloqueantes dentro de la ventana de siete días. La suite
falla si se pierde alguna de estas condiciones. Las ventanas de un día pueden
seguir vacías; se verifican sus respuestas exactas contra el fixture.

El manifiesto identifica la revisión del dataset. Al comparar Q05 con v1,
comparar SQL, plan, candidatas, descartes y cardinalidades; los tiempos no son
directamente equivalentes porque cambiaron los estados y las estadísticas.
Aquella corrección no agregó VACUUM ni índices. La segunda fase agrega el
mantenimiento y variantes descritos arriba, conservando los escenarios originales.

Se trata de una distribución sintética de estrés moderado, no de una agenda
operativa validada: puede haber solapamientos, y los turnos usan el servicio común.
No representa frecuencia, ocupación, tamaño de textos ni antigüedad física de
producción. No hay sesiones, tokens, datos personales, contactos ni credenciales
exportadas. Los nombres son etiquetas sintéticas.

Se valida base inicialmente vacía, exactamente 50.000 turnos y coherencia de
negocio entre turno, cliente, servicio y profesional. Las respuestas se comparan
contra el fixture en memoria, por IDs y conteos; Q01 verifica también orden y página.
Q05 verifica la inclusión del turno que cruza el límite. Los datos se confirman
antes de medir; en la segunda fase se ejecuta VACUUM (ANALYZE) de las seis tablas.

## Escenarios

- Q01/Q02: negocios con menor/mayor carga observada; 1/31 días desde medianoche;
  profesional presente/ausente; ASC/DESC; tamaño 50. Agrega última página existente
  de rango amplio en ambos sentidos y un día vacío diez años después.
- Q05: profesionales 1, 20 y 400; 1/7 días, más ventana vacía futura. Estados
  PENDING/CONFIRMED; `starts_at < fin AND ends_at > inicio`.
- Q25: servicios 1 (20 profesionales), 2 (uno) y 20 (ninguno).

Los escenarios originales son 50 combinaciones: 38 de agenda/conteo, 9 de
disponibilidad y 3 de asociaciones. La segunda fase los conserva en `v2-vacuumed`
y agrega los escenarios y variantes de la tabla anterior. Cada combinación genera
un calentamiento y cinco muestras.
La invocación del repositorio usada para captura ya ejecutó una lectura adicional;
se documenta explícitamente y no se presenta ninguna muestra como caché fría.

## SQL real, transacciones y protocolo

Un decorador JDBC local al test observa `prepareStatement`, setters de bindings,
opciones JDBC y `executeQuery`. Los métodos se delegan al driver y se propagan
las excepciones originales. No intercepta ni reemplaza commit/rollback,
autocommit, cierre o unwrap. La captura ThreadLocal está activa sólo durante la
llamada explícita al repositorio y se limpia en finally. No captura SQL de carga.

SQL JDBC expandido y SQL de Hibernate se registran con sus parámetros reales y
tipos Java/métodos setter. Se reproducen sobre PreparedStatement sin interpolar
literales ni simplificar proyecciones, JOIN o IN. Sólo se permiten SELECT en el
runner de esta fase. La conexión debe coincidir exactamente con la URL del
contenedor en ejecución y reportar PostgreSQL major 16, incluso antes de Flyway.

Se configura `prepareThreshold=0` tanto para captura como para reproducción:
planes JDBC sin preparación nombrada en servidor. No caracteriza el comportamiento
de planes genéricos tras el umbral de preparación de un entorno productivo.

Cada EXPLAIN corre en transacción read-only REPEATABLE READ, con `SET LOCAL`
`statement_timeout=120s` y `lock_timeout=5s`; éstos son límites de protección,
no objetivos de rendimiento. Sin pausas ni espera de estabilización. Una muestra
abortada falla la suite, conserva planes anteriores y marca status failed.

```sql
EXPLAIN (ANALYZE, BUFFERS, SETTINGS, FORMAT JSON)
-- SQL capturado, enlazado mediante JDBC
```

No hay índices candidatos, DML medido ni cambios de parámetros del optimizador
para inducir un plan. VACUUM se ejecuta antes de medir. La base recién cargada
difiere de una base envejecida; se registran mantenimiento y autovacuum. No hay
otras consultas concurrentes de esta suite durante la medición.

## Evidencia

| Archivo | Contenido |
| --- | --- |
| `manifest.json` | Commit, versiones Java/Hibernate/JDBC/PostgreSQL, imagen/ID, semilla, reloj, protocolo y límites de recursos reportados por Docker |
| `dataset-summary.json` | Cardinalidades exactas, turnos por negocio/profesional/estado y asociaciones por servicio |
| `flyway-history.json` | Migraciones aplicadas, checksums y éxito |
| `indexes.json`, `pg-indexes.json` | Definiciones, expresiones, predicados, método, PK/UNIQUE, validez y tamaños |
| `constraints.json` | Constraints y definiciones efectivas |
| `statistics.json`, `table-statistics.json` | pg_stats, reltuples/relpages, timestamps de análisis/mantenimiento y tuplas estimadas |
| `settings.json` | Parámetros seleccionados del servidor, sin conexión ni secretos |
| `Qxx-<escenario>-<muestra>.json` | SQL, bindings/opciones, cardinalidad esperada y EXPLAIN completo; muestra 0 es calentamiento |
| `measurements.json` | Todas las muestras completadas, incluso si falla una posterior |
| `summary.csv` | Tiempos individuales, cardinalidad y buffers del nodo raíz |
| `status.json` | complete o failed; tipo de excepción sin mensaje potencialmente sensible |
| `equivalence.json` | Hashes de datos compartidos e IDs de respuestas recientes de Q05 |
| `maintenance-before-vacuum.json`, `maintenance-before-measurement.json`, `maintenance-after-measurement.json` | Estado de mantenimiento y visibilidad por fase |
| `indexes-after.json` | Definiciones finales, comprobadas iguales al catálogo inicial |
| `historical-agenda.json` | Sólo en base: actividad de los 31 días y página profunda completa |

Cada variante conserva su manifiesto, planes, CSV y status independientes. La
raíz agrega manifiesto del protocolo y status con duraciones/resultados de las
cuatro variantes. Los CSV incorporan variante y Heap Fetches; JSON conserva todos
los nodos, incluidas filas descartadas, ordenamientos y loops.

Una falla anterior a crear el directorio puede impedir generar informes. Los
errores funcionales/SQL son fallos; elegir Seq Scan no lo es. Flyway valida
instalación vacía con versiones 1,3,4,1002,1003,1004 y segunda migración sin cambios.
Para actualización se conserva el test existente `FlywayMigrationTest`; la suite
de planes no duplica esos caminos ni carga semillas locales.

## Interpretación y conservación para revisión

Comparar mediana y rango de las cinco muestras excluyendo warmup. Leer el árbol
JSON para filas reales/estimadas, loops, Rows Removed by Filter/Join Filter,
Index Cond, Filter, rechecks, Heap Fetches, métodos/nombres de índices y sorts
(memoria/disco/temporales). CSV no sustituye ese análisis.

No sumar buffers/filas de padres e hijos: sus métricas pueden incluir el mismo
trabajo. Las filas reales/descartadas por nodo se interpretan con sus loops.
No hay contador universal de filas examinadas. Shared Read no demuestra lectura
física del disco. EXPLAIN agrega instrumentación y no mide transferencia HTTP
ni evaluación Java. Un Seq Scan con pocas asociaciones puede ser óptimo.

Concluir únicamente lo respaldado en estos escenarios: costo de Q02 frente a
Q01, orden/página, descarte histórico de Q05 y costo de Q25. Si no hay selectividad
o escala suficientes, documentar resultado inconcluso; ampliar volumen sólo por
una hipótesis concreta. No derivar un índice definitivo de una lectura aislada.

### Manifiesto de evidencia para el PR

La selección proviene de `build/reports/query-plan/` y conserva sus nombres
y rutas relativas en [evidencia-query-plan/](evidencia-query-plan/). El
[manifiesto SHA-256](evidencia-query-plan/SHA256SUMS) lista cada archivo.
Todas las corridas de la tabla se
hicieron el **3 de noviembre de 2026 (UTC)** en **PostgreSQL 16.14,
x86_64 Alpine**. Los manifiestos originales registran la identidad de la
imagen y el commit base `f414b8ffcec2de3d23ab94c183e19af54a73b858`. Los dos
archivos de auditoría aún no estaban versionados al medir. Ese commit no
identifica la versión exacta del laboratorio y no se dispone de un hash de
esas fuentes por corrida.

| Corrida UTC | Variante y escenarios | Archivos | Bytes |
| --- | --- | ---: | ---: |
| `2026-11-03T11-56-20.051107200Z-373e737e16f4` | `v2-vacuumed`, dos ampliaciones de historial y asociaciones ampliadas. Baseline de agenda, conteo, disponibilidad y asociaciones. | 10 | 74.259 |
| `2026-11-03T20-34-32.280784900Z-c0a2df90dbcc-pending` | `critical-pending`. Conflictos, selección y borrado de sesiones y tokens. | 11 | 78.126 |
| `2026-11-03T20-50-45.651741300Z-0bca93e27d99-q05-ends` | `q05-experiment`. Baseline y candidato para base, dos ampliaciones de historial, control futuro y escrituras. | 20 | 48.537 |
| `2026-11-03T21-24-16.081797900Z-fccb3ac305f5-q25-service` | `q25-experiment`. Corrida corregida para ambos volúmenes de asociaciones y escrituras. | 16 | 26.271 |
| `2026-11-03T21-44-35.304649500Z-bd8df575ad8e-q11-union` | `q11-union-experiment`. OR y UNION ALL en tres escalas, vacío y bordes del cutoff. | 7 | 29.434 |
| **Total de archivos de medición** | El manifiesto SHA-256 se contabiliza por separado. | **64** | **256.627** |

Selección por corrida, con rutas relativas al directorio de cada una:

- **Común** · `manifest.json` y `status.json` de la raíz en las cinco corridas.
- **Baseline** · `summary.csv` en `v2-vacuumed/`, `history-plus-2000/`,
  `history-plus-8000/` y `associations-expanded/`. En `v2-vacuumed/`,
  los cuatro planes de muestra 1 con el patrón
  `Q{01,02}-historical31-b2-d31-descfalse-staffnull-p{0,65}-2026-09-28-1.json`.
- **Consultas críticas** · `summary.csv`, `indexes.json`, `flyway-history.json`,
  `Q04-low-history-1.json`, `Q04-history-plus-8000-1.json` y
  `Q{11,12,16,17}-n20000-eligible50-1.json`. El catálogo cubre los 43 índices
  funcionales y los dos de Flyway.
- **Q05** · `summary.csv` en los brazos `baseline/` y `candidate/` de
  `base/`, `history-plus-2000/`, `history-plus-8000/`, `future-control/`
  y `writes/`. En ambos brazos de `history-plus-8000/` y `future-control/`,
  `Q05-staff1-d7-2026-10-28-1.json`. En ambos brazos de `writes/`,
  `Q05-write-insert-100-1.json` y `Q05-write-reschedule-100-1.json`.
- **Q25** · `summary.csv` en ambos brazos de `base/`, `associations-expanded/`
  y `writes/`. En ambos brazos de `associations-expanded/`,
  `Q25-service1-1.json`. En ambos brazos de `writes/`,
  `Q25-write-insert-100-1.json` y `Q25-write-delete-100-1.json`. Para el
  tamaño del índice, `base/candidate/preparation.json` y
  `associations-expanded/candidate/preparation.json`.
- **Q11** · `summary.csv` de la raíz y, para cada escenario
  `n20000-eligible50` y `n5000-eligible1000`, los planes
  `<escenario>/Q11-{or,union-all}-<escenario>-1.json`.

Los CSV permiten recalcular medianas y rangos excluyendo calentamientos. Los
planes elegidos muestran operadores, cardinalidades, descartes y WAL donde
corresponde. Son muestras ilustrativas, no planes elegidos por su latencia.
La selección deja fuera agregados JSON voluminosos y corridas superadas.
Los hashes se verificaron sobre los bytes originales. Para repetir la
verificación desde el directorio de evidencia, ejecutar
`sha256sum --check SHA256SUMS`. Los JSON originales usan CRLF y los CSV,
LF. Una futura incorporación o extracción con conversión de finales de línea
invalidaría los hashes. Usar Git con `core.autocrlf=false` para esas
operaciones sobre esta carpeta y verificar los hashes de nuevo.
Conservar el resto del historial fuera de Git si se
necesita para reproducir el análisis original. La versión menor de PostgreSQL,
las estadísticas y la caché pueden afectar planes y tiempos de una repetición.

## Tercera fase de consultas críticas

Con `TURNERO_QUERY_PLAN_AUDIT=true` y Java 21, ejecutar desde el repositorio:

```powershell
.\gradlew.bat compileTestJava
.\gradlew.bat test --tests "com.turnero.api.integration.MvpQueryPlanPostgresIT.measurePendingCriticalQueries" --rerun-tasks
```

El filtro selecciona sólo el método nuevo. Los informes se guardan en
`build/reports/query-plan/<instante>-<container-id>-pending/`. El método existente
`measureExistingIndexesOnDeterministicDataset` conserva los escenarios de segunda
fase. Seleccionar ese método por nombre para repetir exclusivamente esa fase.

| Consulta | Repositorio y escenarios |
| --- | --- |
| Q04 | `AppointmentRepository.findByStaffMemberIdAndBusinessId`. P400 con 67 turnos, P1 con 2.427 y P1 con 10.427 tras agregar historial antiguo. El SQL trae el historial completo. El tiempo de captura incluye materialización JDBC/JPA y validación de IDs. La comprobación Java de solapamiento se realiza en el servicio productivo y queda fuera de EXPLAIN. |
| Q11/Q12 | `UserSessionRepository.findCleanupCandidateIds` y `deleteCleanupCandidatesByIds`. Fixture de 5.000 sesiones con 50 o 1.000 elegibles y 20.000 con las mismas 50. El cutoff fijo usa la referencia local y 30 días de retención. |
| Q16/Q17 | `AppointmentPublicTokenRepository.findCleanupCandidateIds` y `deleteCleanupCandidatesByIds`. Mismos tamaños y elegibles para negocio 1. En 20.000 filas se agregan tokens de otros negocios y se conserva el conjunto del negocio medido. Cutoff desde el instante fijo en la zona del negocio. |

Las selecciones respetan `ORDER BY id` y presupuesto 500. Q16 también verifica
presupuesto 17. Los fixtures incluyen expiración, revocación o uso antes, igual
y después del cutoff. Un marcador reciente prevalece sobre la expiración antigua.
Q16/Q17 verifican pertenencia al negocio mediante turnos existentes. Los controles
de revalidación incluyen IDs no elegibles y, para tokens, un ID elegible ajeno.
La selección vacía se verifica sin invocar el DELETE.

Se captura primero el SELECT y el DELETE reales del repositorio en una transacción
marcada para rollback desde su inicio. El borrado capturado usa el lote devuelto
por el selector. Se comprueban cantidad afectada, sobrevivientes y restauración.
El control de revalidación corre en otra transacción de rollback. Después se
reconstruye la tabla objetivo antes de medir.

Cada muestra de DELETE parte de una carga por lotes de la tabla objetivo, seguida
de `VACUUM (ANALYZE)` con autocommit fuera de transacciones explícitas. Usuarios
y turnos de referencia se conservan. La URL, la versión y la identidad del
contenedor descartable se verifican antes de truncar o ejecutar un borrado.
Dentro de una transacción de escritura marcada para rollback se vuelve a
seleccionar el mismo lote y se ejecuta
`EXPLAIN (ANALYZE, BUFFERS, SETTINGS, WAL, FORMAT JSON)`. Se comprueban los IDs
que permanecen dentro de la transacción y todos los IDs y campos no secretos
después de revertirla. Las filas afectadas se deducen del conjunto de IDs,
independientemente del contador del nodo raíz de EXPLAIN.

La fase genera 90 planes, con cinco muestras y un calentamiento por escenario.
`select-summary.csv` contiene Q04/Q11/Q16 y `delete-summary.csv` contiene
Q12/Q17. Los planes JSON de DELETE conservan WAL, verificaciones y tiempos de
preparación. Los archivos de fixture y preparación separan carga, mantenimiento,
captura y medición. Los planes contienen IDs sintéticos y fechas. La exportación
de estadísticas excluye columnas de hashes de sesión/token. Se verifica el
catálogo de 43 índices funcionales contra las definiciones iniciales de Flyway.

En Q04 interesa cuánto historial debe materializar el repositorio. En Q11/Q16,
comparar los índices parciales, el recorrido por PK para ordenar el lote y, en
tokens, el acceso por turno y negocio. En Q12/Q17, comparar acceso por IDs,
revalidación y WAL. El rollback conserva las filas, pero el trabajo del DELETE
ya se ejecutó. La reconstrucción evita acumular efectos físicos entre muestras.
Los planes siguen usando datos sintéticos y caché calentada. La medición de
tokens cubre una iteración por negocio del servicio de limpieza.

## Experimento Q05 con índice por fin de turno

Con Java 21 y `TURNERO_QUERY_PLAN_AUDIT=true`, ejecutar sólo este método:

```powershell
.\gradlew.bat test --tests "com.turnero.api.integration.MvpQueryPlanPostgresIT.measureQ05EndsAtCandidate" --rerun-tasks
```

La suite crea el siguiente índice exclusivamente en la base PostgreSQL 16 del
contenedor verificado. El catálogo original se comprueba antes y después de
cada brazo. El único índice que el test elimina explícitamente es el candidato.

```sql
CREATE INDEX exp_appointments_staff_ends_at
    ON appointments (staff_member_id, ends_at);
```

Se reconstruye cada brazo desde el fixture v2, con la misma semilla y los mismos
IDs. Los datasets son base, +2.000 y +8.000 turnos antiguos de P1, y un control
con 8.000 turnos confirmados de P1 a partir del octavo día. Cada brazo mide P1
en ventanas de un día, siete días y el caso vacío de 2036. Se alterna el orden
baseline/candidato entre datasets. La huella de la tabla, el SQL y los bindings
se comparan entre brazos. Los IDs devueltos deben coincidir con el fixture.

Autovacuum queda deshabilitado sólo en el contenedor. Tras cargar y, en su brazo,
crear el índice, ambos brazos reciben el mismo `VACUUM (ANALYZE)` fuera del tramo
medido. Se conservan estadísticas, visibilidad, configuración, tamaño del
índice, tiempos de preparación y planes JSON. El optimizador elige el acceso
sin forzarlo. Las muestras son de caché calentada.

Las escrituras se miden después de las lecturas, sobre dos reconstrucciones
independientes del fixture base. Cada muestra realiza una operación SQL sobre
100 filas: alta copiando datos sintéticos con IDs explícitos, reprogramación
desplazando inicio y fin un día, o baja física. Cada operación usa EXPLAIN con
WAL dentro de una transacción marcada para rollback. Se comprueban las filas
afectadas dentro de la transacción y la huella restaurada después. Antes de
cada muestra se ejecuta VACUUM (ANALYZE) de appointments, fuera de la transacción.
El rollback verifica equivalencia lógica. VACUUM y el estado de los checkpoints
pueden dejar diferencias físicas entre muestras. Los tiempos de las operaciones
excluyen mantenimiento, servicios Java y commit. El WAL de vacuum y la
latencia del flujo productivo de escritura quedan fuera de esta medición.

El directorio nuevo `*-q05-ends` separa datasets y brazos. Se generan 144 planes
de lectura y 36 de escritura, incluidos calentamientos. Comparar medianas y
rangos junto con el índice elegido, candidatas, descartes y buffers. Para
escritura, comparar registros, bytes e imágenes de página completa de WAL.
El control futuro permite detectar si el acceso por fin sólo desplaza el
descarte hacia las reservas posteriores a la ventana.

La medición Q05 quedó validada. El índice por profesional y fin permanece como
candidato condicionado, fuera de Flyway.

## Experimento Q25 con índice por servicio

Con Java 21 y `TURNERO_QUERY_PLAN_AUDIT=true`, ejecutar el método específico.

```powershell
.\gradlew.bat test --tests "com.turnero.api.integration.MvpQueryPlanPostgresIT.measureQ25ServiceCandidate" --rerun-tasks
```

El candidato se crea en PostgreSQL 16, dentro del contenedor verificado.

```sql
CREATE INDEX exp_staff_service_offerings_service
    ON staff_service_offerings (service_offering_id);
```

Los brazos de 1.960 y 7.220 asociaciones se reconstruyen independientemente.
La huella incluye IDs, profesionales y servicios. Cada brazo captura el SQL y
los parámetros del repositorio real y verifica las respuestas de los servicios
1, 2 y 20, con 20, 1 y 0 asociaciones respectivamente. Ambos brazos reciben
VACUUM (ANALYZE) con autovacuum deshabilitado sólo en el contenedor. El
optimizador elige el plan y se registran los índices y la visibilidad efectiva.

Después de terminar las lecturas se reconstruye el fixture de 7.220 asociaciones
para cada brazo de escritura. Las altas agregan 100 pares válidos en servicios
sin asociaciones y las bajas retiran las primeras 100 filas. Se verifica la
operación dentro de la transacción y se compara la huella completa después del
rollback. Antes de cada muestra se hace VACUUM (ANALYZE) de la tabla fuera de la
transacción. Los planes de escritura incluyen WAL. El tiempo de preparación y
el mantenimiento posterior a cada operación se mantienen fuera de EXPLAIN.

El directorio `*-q25-service` separa 72 planes de lectura y 24 de escritura,
incluidos los calentamientos. Los 43 índices funcionales se verifican contra
el catálogo inicial. El UNIQUE `(staff_member_id, service_offering_id)` mantiene
la integridad del par y el acceso por profesional. El candidato usa el servicio
como primera clave para Q25 y agrega espacio y mantenimiento de escritura.

La corrida verificada `2026-11-03T21-24-16.081797900Z-fccb3ac305f5-q25-service`
completó los 96 planes en PostgreSQL 16.14. En ambos tamaños, el baseline usó
Seq Scan y el candidato limitó el acceso a 20, 1 o 0 filas del heap. Los
buffers hit bajaron de 13 a 2–3 en la base y de 46 a 2–3 en la ampliación.
El índice ocupó 40 KiB y 72 KiB respectivamente. Se preservaron la huella,
el SQL, los parámetros y los resultados de cada par.

En la corrida verificada, el ahorro de mediana por lectura fue de 0,062 a
0,971 ms. Las altas de 100 filas pasaron de 514 a 714 buffers hit y de
21.500 a 28.012 bytes de WAL. Las bajas conservaron 104 buffers hit y
5.400 bytes de WAL en la sentencia. Se verificaron los 24 rollbacks.
Son mediciones sintéticas con caché calentada y cinco muestras por escenario.
Las escrituras usan SQL directo y rollback, con el costo de commit, servicios
Java y vacuum fuera del tiempo y WAL de cada sentencia. Con este ahorro absoluto
y sin una frecuencia medida de Q25 en el flujo real, se recomienda conservar
el candidato para evaluación y posponer la migración.

## Experimento Q11 con UNION ALL

Con Java 21 y `TURNERO_QUERY_PLAN_AUDIT=true`, ejecutar el método específico.

```powershell
.\gradlew.bat test --tests "com.turnero.api.integration.MvpQueryPlanPostgresIT.measureQ11UnionAll" --rerun-tasks
```

El baseline captura el SQL del repositorio con `PageRequest.of(0, 500)`. El
predicado usa `revoked_at < cutoff` para las sesiones revocadas y sólo consulta
`expires_at < cutoff` cuando `revoked_at` es nulo. La alternativa de laboratorio
conserva esos predicados en dos ramas disjuntas, cada una compatible con un
índice parcial de Flyway V3.

```sql
SELECT id FROM (
    SELECT id FROM user_sessions WHERE revoked_at IS NOT NULL AND revoked_at < ?
    UNION ALL
    SELECT id FROM user_sessions WHERE revoked_at IS NULL AND expires_at < ?
) AS eligible
ORDER BY id ASC
LIMIT ?
```

La clave primaria hace único el ID y ninguna sesión cumple ambos predicados.
El orden y el límite de 500 se aplican después de unir las ramas. La prueba
comprueba bindings idénticos y los IDs exactos en orden antes de tomar cualquier
muestra. Las fechas se prueban un microsegundo antes, justo en el cutoff y un
microsegundo después, incluyendo la precedencia de una revocación presente.
El caso de bordes devuelve los IDs 2, 4 y 7. El caso vacío usa un cutoff más
antiguo sobre el fixture de 5.000 sesiones.

Cada caso se verifica primero y se reconstruye para la medición, comprobando
su huella. Los dos brazos leen el mismo fixture reconstruido. Las escalas son
5.000/50, 5.000/1.000 y 20.000/50 sesiones/elegibles. La preparación reutiliza
VACUUM (ANALYZE) con autovacuum deshabilitado sólo en el contenedor. El orden de
los brazos se alterna entre casos y el optimizador elige el plan libremente.

El directorio `*-q11-union` guarda 60 planes, incluidos calentamientos, con SQL,
bindings, IDs, estadísticas y catálogo. Los nodos JSON conservan filas, descartes,
índices y ordenamientos. Los 43 índices funcionales se comprueban contra el
catálogo inicial. El experimento compara únicamente la selección.

La corrida verificada `2026-11-03T21-44-35.304649500Z-bd8df575ad8e-q11-union`
completó 60 planes en PostgreSQL 16.14. Los dos parciales de V3 aparecieron en
UNION ALL para las tres escalas y la selección vacía. En las tres escalas,
el OR eligió la PK y obtuvo el orden por ID durante el recorrido.

| Sesiones / elegibles | Filas de tabla revisadas, OR → UNION ALL | Buffers hit | Mediana, ms |
| --- | ---: | ---: | ---: |
| 5.000 / 50 | 5.000 → 50 | 80 → 8 | 1,716 → 0,254 |
| 5.000 / 1.000 | 999 → 1.000 | 18 → 56 | 0,553 → 0,783 |
| 20.000 / 50 | 20.000 → 50 | 313 → 8 | 8,259 → 0,331 |

UNION ALL agrega Append y un quicksort global de 25 KiB. Con 1.000 elegibles
ordenó los 1.000 IDs para entregar 500, mientras que el OR pudo detener el
recorrido de la PK al llenar el lote. En vacío, el OR también usó ambos
parciales mediante BitmapOr y los dos brazos tocaron cuatro buffers. En el
fixture de bordes, el OR recorrió siete filas y UNION ALL recorrió siete por
rama mediante Seq Scan. Los IDs y el orden coincidieron en todos los casos.

La alternativa separa la elegibilidad en dos ramas que deben mantenerse
sincronizadas con la semántica productiva y conserva un cutoff común. El
beneficio se observó cuando había pocos elegibles respecto del total,
mientras que un lote que se llena temprano favoreció
el recorrido por PK. Las cinco muestras por brazo usaron caché calentada y
datos sintéticos. La proporción y posición de los elegibles por ID influyen
en el costo del recorrido y limitan la extrapolación al flujo real.

## Etapas pendientes

- Ensayos de Q01 y Q04, pendientes de autorización.
- TURN-107/dashboard: pendiente hasta disponer de consultas implementadas.
