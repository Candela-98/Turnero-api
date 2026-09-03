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
2. Converger auth y proteccion admin mediante TURN-88 y TURN-89 para desbloquear la integracion frontend.
3. Cerrar agenda, invariantes de escritura y availability mediante TURN-90, TURN-105, TURN-109 y TURN-92.
4. Completar el resumen operativo mediante las subtareas de TURN-93.
5. Implementar booking publico y cancelacion mediante TURN-58 a TURN-61.
6. Cerrar el hardening operativo de TURN-32 despues de estabilizar los flujos de los que depende. TURN-62 puede anticipar la decision BFF/CORS/CSRF; las demas subtareas respetan los bloqueos registrados en Jira y el tracking.
7. Definir el plan AWS antes de preparar ambientes compartidos `desa` o `prod`.

## Riesgos a mantener visibles

- La disponibilidad es informativa: toda creacion o edicion de appointment debe revalidarla dentro de la escritura.
- Auth y contexto de negocio ya tienen una implementacion base, pero TURN-88 y TURN-89 deben cerrar contrato, autorizacion y proteccion antes de considerarlos definitivos.
- Las decisiones de CORS, CSRF, secretos, concurrencia y rate limiting se cierran mediante TURN-32; backups y rollback pertenecen al plan de deploy.

## Criterio para elegir el proximo trabajo

No abrir un feature por este documento. Primero consultar el tracking y Jira; luego tomar la siguiente subtarea del plan cuyo alcance y dependencias esten listos. Si una tarea cambia comportamiento, contrato o persistencia, actualizar respectivamente el documento fuente correspondiente y el tracking al mergearse.
