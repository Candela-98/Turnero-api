# Turnos locales desde hoy

Complemento **opcional** del aprovisionamiento del negocio/owner. Usa Python 3.9+ (biblioteca estándar) y la API autenticada, sin SQL ni dependencias adicionales. El backend y PostgreSQL deben estar levantados, las migraciones aplicadas y la sesión del OWNER iniciada. El aprovisionamiento de identidad no crea automáticamente un catálogo listo para reservar.

## Uso

1. Preparar el entorno con `make db-up`, el aprovisionamiento habitual y `make run`; iniciar sesión en el frontend. Deben existir clientes activos, profesionales activos con servicios activos asignados, horarios del negocio/profesional y reglas de reserva.
2. Obtener el ID del negocio de la respuesta local `GET /business` o `/auth/me`. El script lo compara con el negocio autenticado antes de continuar; no tiene IDs de personas ni nombres de empresa fijos.
3. En DevTools → Network, abrir una petición autenticada local y copiar únicamente el valor de la cabecera **Cookie** (`turnero_session=…`). No compartirlo ni guardarlo en el repo.
4. Desde `Turnero-api`, simular:

   ```bash
   make seed-local-appointments BUSINESS_ID=1
   ```

   Solicita la cookie de manera oculta en terminal. Muestra negocio/zona/rango/cantidad existente, nuevas altas y distribución. **La simulación no escribe.**

5. Si la capacidad alcanza, ejecutar:

   ```bash
   make seed-local-appointments BUSINESS_ID=1 AGENDA_APPLY=1
   ```

   Vuelve a pedir la cookie. Realiza los POST secuencialmente y verifica el lote al terminar.

Por defecto son **106 turnos totales**, distribuidos entre **hoy y los dos días siguientes** según la zona IANA de `GET /business`, sin usar la fecha del navegador. No son 106 por día. Equilibra días y clientes cuando hay capacidad; no inventa horarios para llenar un día cerrado.

Opciones:

```bash
make seed-local-appointments BUSINESS_ID=1 AGENDA_COUNT=60 AGENDA_DAYS=3
make seed-local-appointments BUSINESS_ID=1 AGENDA_BASE_URL=http://localhost:3000/api/backend
python3 scripts/seed-local-appointments.py --business-id 1 --count 106 --days 3 --apply
```

Para ejecución sin terminal interactiva acepta `TURNERO_SESSION_COOKIE` en el entorno del proceso; no recibe secretos como argumentos ni los imprime. Autenticación por cookie de sesión del OWNER, igual que la aplicación; no usa bearer tokens ni crea sesiones por SQL. Rechaza endpoints remotos, redirecciones y perfil distinto de `dev`. La URL base no incluye `/api/v1`; para probar el BFF usar la URL del ejemplo.

## Reglas y límites

- Lee todos los profesionales/clientes paginados del negocio y reutiliza activos. Si faltan referencias, falla sin crearlas ni cambiar estados. El bloqueo del cliente restringe reservas públicas, no estas altas ADMIN; no se consulta ni expone el motivo privado.
- Lee los servicios asignados al profesional y usa duración/precio reales de un servicio activo. Elige primero los huecos con final más temprano para aprovechar la capacidad, sin prometer una distribución igual entre servicios.
- Consulta `GET /availability/slots`: reutiliza intervalo, horario efectivo del negocio/profesional y excepciones, sin duplicar su cálculo en el script. Descarta horas pasadas y deja al menos 15 minutos desde la planificación. Descarta horas locales ambiguas/inexistentes durante DST para este fixture.
- Como el alta admin comprueba cruces del profesional contra **todos** los estados pero disponibilidad solo considera PENDING/CONFIRMED, también lee todos los turnos de cada día y del día anterior. Evita cruces por profesional **y cliente**, incluidos turnos de otro día que terminan hoy.
- Genera solo PENDING/CONFIRMED con source ADMIN y horarios futuros. No crea completados/ausentes en el futuro para simular badges. Anticipación/ventana de reservas públicas, login de cliente y bloqueo público corresponden al flujo público; el script sigue el contrato administrativo y no simula una reserva PUBLIC_BOOKING.
- Antes de cada POST vuelve a leer referencias, servicios, disponibilidad y conflictos. Si el catálogo/capacidad cambió, se detiene. El POST ejecuta además las validaciones reales del backend, incluido `@Future`; no cambia ninguna regla del servidor.
- Solo realiza altas tras encontrar el plan completo. Si pide 106 pero encuentra 80, informa `80/106` y no escribe: reducir explícitamente `AGENDA_COUNT` o preparar más capacidad válida. No extiende fechas, abre domingos, reactiva profesionales ni reduce duraciones para forzar el total. La capacidad informada es la encontrada por el planificador, no una prueba de optimización global.

## Reejecución y fallos

Marca las altas con `internal_notes = Fixture local agenda v2:<business>:<fecha-inicial>:<días>`. Repetir el mismo rango en el mismo día completa **hasta** el objetivo sin duplicar; aumentar el objetivo agrega la diferencia. Reducirlo no borra el excedente. Al día siguiente se trata de otro lote, teniendo en cuenta las reservas ya existentes como ocupadas. No modifica los fixtures históricos TURN-90/70.

No existe una transacción masiva en la API. Si falla el POST 41, se informa cuántos anteriores se confirmaron; no se borran ni reprograman datos. Un timeout podría ocurrir después de guardar: reejecutar el mismo lote reconcilia la lectura, sin reintentar ese POST a ciegas. Ejecutar una sola siembra por negocio a la vez y evitar editar la agenda durante la carga: la API actual bloquea cruces concurrentes del profesional, pero no ofrece idempotencia ni bloqueo atómico del cliente entre profesionales. La comprobación final detecta cruces sobre las altas creadas; no los repara ni borra.

## Verificación

```bash
make test-local-appointments
```

15 pruebas automáticas del planificador, paginación y cliente HTTP: 106 turnos en tres días sin cruces, zona del negocio, catálogo faltante/inactivo, aislamiento de negocio, día anterior, bloqueos de cualquier estado, capacidad insuficiente sin POST, escritura camelCase/cookie, errores sin secretos, revalidación, reejecución y recuperación parcial. Usan una API simulada y no modifican PostgreSQL ni reemplazan una prueba autenticada contra tu entorno.

Después de aplicar, abrir `/agenda`, Hoy y los dos días siguientes, comprobar cantidades del resumen, profesional/cliente/servicio y horarios. El script requiere el contrato enriquecido de TURN-90 en ambas lecturas; actualizar/reiniciar el backend si devuelve una colección antigua.

Los SQL históricos `seed-local-agenda.sql`, `reuse-local-agenda-catalog.sql` y `schedule-local-agenda.sql` fueron herramientas de QA de TURN-90/70. Para nuevas cargas compartidas usar este script por API; no volver a insertar o reprogramar históricos directamente como rutina.
