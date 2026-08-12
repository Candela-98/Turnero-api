# Turnero API - Proximos pasos MVP

## Proposito

Este documento orienta las prioridades de producto y tecnica para continuar el MVP. No es fuente de estado de implementacion ni duplica contratos, flujos o alcance de PRs.

Usar cada documento para su responsabilidad:

| Necesidad | Fuente de verdad |
| --- | --- |
| Saber que esta mergeado, que falta y que ramas siguen pendientes | `tracking-implementacion-mvp.md` |
| Implementar una subtarea y conocer su alcance, tests y criterios | `plan-migracion-backend-mvp.md` y Jira |
| Resolver comportamiento de negocio | `flujos-funcionales-mvp.md` |
| Implementar o consumir HTTP | `api-contracts-mvp.md` |
| Resolver persistencia | `schema-db-mvp.md` |
| Implementar login y sesiones | `auth/google-mvp.md` |

## Contexto MVP estable

- Caso inicial: tiendas chicas, un negocio por cuenta y agenda diaria como centro operativo.
- El owner usa Google; no hay registro publico self-service de negocios.
- El cliente final reserva sin login obligatorio y cancela mediante token seguro.
- PostgreSQL es la base objetivo; el deploy previsto es AWS.

Estas decisiones estan detalladas en los documentos fuente y no deben redefinirse aqui.

## Prioridades vigentes

1. Verificar la base tecnica local y de CI con Docker/Testcontainers disponible, para que la migracion Flyway sobre PostgreSQL forme parte de una suite verde.
2. Cerrar agenda diaria y creacion de appointments contra el contrato MVP. El alcance exacto es `PR 5` en `plan-migracion-backend-mvp.md`; el avance efectivo vive en el tracking.
3. Revisar las ramas pendientes y abordar availability admin solo despues de fijar la agenda. El algoritmo se define durante ese trabajo con apoyo de flujos, contrato, schema y tests.
4. Continuar la gestion admin restante segun el orden del plan de migracion.
5. Implementar Auth Google y proteccion de endpoints admin antes de integrar booking publico real.
6. Definir el plan AWS antes de preparar ambientes compartidos `desa` o `prod`.

## Riesgos a mantener visibles

- La disponibilidad es informativa: toda creacion o edicion de appointment debe revalidarla dentro de la escritura.
- El contexto de negocio actual en desarrollo es temporal; no reemplaza autenticacion ni autorizacion reales.
- Las decisiones de CORS, CSRF, secretos, concurrencia, rate limiting, backups y rollback deben cerrarse antes de produccion.

## Criterio para elegir el proximo trabajo

No abrir un feature por este documento. Primero consultar el tracking y Jira; luego tomar la siguiente subtarea del plan cuyo alcance y dependencias esten listos. Si una tarea cambia comportamiento, contrato o persistencia, actualizar respectivamente el documento fuente correspondiente y el tracking al mergearse.
