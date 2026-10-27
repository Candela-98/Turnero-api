# Verificación autenticada del script local — 2026-10-24

Negocio local 1, zona America/Argentina/Buenos_Aires. Ejecución inicial el sábado después de las 22:00: hoy ya cerrado, domingo/lunes configurados cerrados. Antes del cambio el rango calendario informaba 0/106 y no realizó altas.

Con la selección de días disponibles, simulación y alta por API reales:

| Fecha elegida | Turnos |
| --- | ---: |
| 2026-10-27 | 36 |
| 2026-10-28 | 35 |
| 2026-10-29 | 35 |
| Total | 106 |

- 53 PENDING, 53 CONFIRMED, todos futuros y source ADMIN.
- Reutiliza clientes/profesionales activos del negocio y servicios activos asignados; precio y duración coinciden con catálogo y fin calculado.
- Revalidación de disponibilidad/referencias antes de cada POST, verificación final del script y auditoría independiente por API. Sin cruces por profesional/cliente con ningún estado, incluidos existentes y día previo.
- Reejecución simulada: lote existente 106, nuevas altas 0; conserva las tres fechas del lote aunque su ocupación altere los slots libres.
- Históricos anteriores intactos. Archivo temporal de sesión eliminado después de la comprobación; ninguna cookie ni datos privados se guardan en esta evidencia.
- 18 tests Python aprobados. Revisión visual del usuario sobre /agenda?date=2026-10-27 pendiente. No se modificaron reglas de negocio ni servidor.
