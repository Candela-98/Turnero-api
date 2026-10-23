-- Solo base LOCAL. Ejecutar con psql -v business_id=<negocio de sesión> -v agenda_date=2026-10-20 -f scripts/seed-local-agenda.sql
-- Crea 106 turnos ficticios, NO el volumen del benchmark. No modifica turnos existentes.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE agenda_seed_context AS SELECT id AS business_id, :'agenda_date'::date AS day FROM businesses WHERE id=:'business_id'::bigint;
DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM agenda_seed_context) THEN RAISE EXCEPTION 'Business not found'; END IF; END $$;
WITH inserted AS (
 INSERT INTO customers(business_id,name,email,status,created_at,updated_at)
 SELECT business_id,'QA Agenda Cliente','agenda-qa@example.invalid','INACTIVE',now(),now() FROM agenda_seed_context RETURNING id
) SELECT id AS customer_id FROM inserted \gset
WITH inserted AS (
 INSERT INTO staff_members(business_id,name,role_label,status,created_at,updated_at)
 SELECT business_id,'QA Agenda Profesional activo','Profesional','ACTIVE',now(),now() FROM agenda_seed_context RETURNING id
) SELECT id AS staff_id FROM inserted \gset
WITH inserted AS (
 INSERT INTO staff_members(business_id,name,role_label,status,deleted_at,created_at,updated_at)
 SELECT business_id,'QA Agenda Profesional histórico eliminado','Profesional','INACTIVE',now(),now(),now() FROM agenda_seed_context RETURNING id
) SELECT id AS historical_staff_id FROM inserted \gset
WITH inserted AS (
 INSERT INTO service_offerings(business_id,name,category,duration_minutes,price_cents,status,deleted_at,created_at,updated_at)
 SELECT business_id,'QA Agenda Corte y cuidado','QA',60,5000,'INACTIVE',now(),now(),now() FROM agenda_seed_context RETURNING id
) SELECT id AS service_id FROM inserted \gset
INSERT INTO appointments(business_id,customer_id,staff_member_id,service_offering_id,starts_at,ends_at,duration_minutes,price_cents,status,source,customer_notes,created_at,updated_at)
SELECT business_id,:'customer_id'::bigint,CASE WHEN n=106 THEN :'historical_staff_id'::bigint ELSE :'staff_id'::bigint END,:'service_id'::bigint,
 day + CASE WHEN n=106 THEN time '23:30' WHEN n<4 THEN time '07:15' ELSE time '14:00' END,
 day + CASE WHEN n=106 THEN interval '1 day 00:30' WHEN n<4 THEN interval '08:15' ELSE interval '15:00' END,
 60,5000,(ARRAY['PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW'])[(n-1)%5+1],'ADMIN','Fixture local TURN-90/70',now(),now()
FROM agenda_seed_context CROSS JOIN generate_series(1,106) n;
COMMIT;
