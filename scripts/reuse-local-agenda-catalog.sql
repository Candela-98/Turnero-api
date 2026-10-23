-- Solo DB LOCAL. Reasigna exclusivamente fixtures TURN-90/70 de la fecha indicada.
-- psql "$DATABASE_URL" -v business_id=1 -v agenda_date=2026-10-20 -f scripts/reuse-local-agenda-catalog.sql
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE agenda_reuse_context AS SELECT id AS business_id, :'agenda_date'::date AS day FROM businesses WHERE id=:'business_id'::bigint;
CREATE TEMP TABLE agenda_reuse_staff AS
SELECT p.id, row_number() OVER (ORDER BY p.id) AS position, count(*) OVER () AS total
FROM staff_members p JOIN agenda_reuse_context b ON p.business_id=b.business_id
WHERE p.deleted_at IS NULL AND p.name NOT LIKE 'QA Agenda%'
 AND EXISTS (SELECT 1 FROM staff_service_offerings r JOIN service_offerings s ON s.id=r.service_offering_id
   WHERE r.staff_member_id=p.id AND s.business_id=p.business_id AND s.status='ACTIVE' AND s.deleted_at IS NULL AND s.name NOT LIKE 'QA Agenda%');
CREATE TEMP TABLE agenda_reuse_customers AS
SELECT c.id, row_number() OVER (ORDER BY c.id) AS position, count(*) OVER () AS total
FROM customers c JOIN agenda_reuse_context b ON c.business_id=b.business_id WHERE c.name NOT LIKE 'QA Agenda%';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM agenda_reuse_staff) OR NOT EXISTS (SELECT 1 FROM agenda_reuse_customers)
 THEN RAISE EXCEPTION 'Existing customers and professionals with active assigned services are required'; END IF;
END $$;
WITH fixtures AS (
 SELECT a.id, a.starts_at, row_number() OVER (ORDER BY a.id) AS position, count(*) OVER () AS total
 FROM appointments a JOIN agenda_reuse_context b ON a.business_id=b.business_id
 WHERE a.customer_notes='Fixture local TURN-90/70' AND a.starts_at>=b.day AND a.starts_at<b.day+interval '1 day'
), references_to_use AS (
 SELECT f.*, c.id AS customer_id,
 CASE WHEN f.position=f.total THEN coalesce((
   SELECT p.id FROM staff_members p JOIN agenda_reuse_context b ON p.business_id=b.business_id
   WHERE p.deleted_at IS NOT NULL AND p.name NOT LIKE 'QA Agenda%'
   AND EXISTS (SELECT 1 FROM staff_service_offerings r JOIN service_offerings s ON s.id=r.service_offering_id
     WHERE r.staff_member_id=p.id AND s.business_id=p.business_id AND s.status='ACTIVE' AND s.deleted_at IS NULL AND s.name NOT LIKE 'QA Agenda%')
   ORDER BY p.id LIMIT 1), p.id) ELSE p.id END AS staff_id
 FROM fixtures f JOIN agenda_reuse_staff p ON p.position=(f.position-1)%p.total+1
 JOIN agenda_reuse_customers c ON c.position=(f.position-1)%c.total+1
), prepared AS (
 SELECT f.*, s.id AS service_id, s.duration_minutes, s.price_cents
 FROM references_to_use f CROSS JOIN LATERAL (
   SELECT s.* FROM service_offerings s JOIN staff_service_offerings r ON r.service_offering_id=s.id
   JOIN agenda_reuse_context b ON s.business_id=b.business_id
   WHERE r.staff_member_id=f.staff_id AND s.status='ACTIVE' AND s.deleted_at IS NULL AND s.name NOT LIKE 'QA Agenda%'
   ORDER BY s.id LIMIT 1 OFFSET ((f.position-1)/4) % (
     SELECT count(*) FROM service_offerings s2 JOIN staff_service_offerings r2 ON r2.service_offering_id=s2.id
     JOIN agenda_reuse_context b2 ON s2.business_id=b2.business_id
     WHERE r2.staff_member_id=f.staff_id AND s2.status='ACTIVE' AND s2.deleted_at IS NULL AND s2.name NOT LIKE 'QA Agenda%')
 ) s
)
UPDATE appointments a SET customer_id=p.customer_id, staff_member_id=p.staff_id, service_offering_id=p.service_id,
 duration_minutes=p.duration_minutes, price_cents=p.price_cents,
 ends_at=a.starts_at+make_interval(mins=>p.duration_minutes), updated_at=now()
FROM prepared p WHERE a.id=p.id;
-- Retira del catálogo solo el profesional sintético creado por el seed que quedó sin turnos.
UPDATE staff_members p SET status='INACTIVE', deleted_at=coalesce(deleted_at,now()), updated_at=now()
FROM agenda_reuse_context b WHERE p.business_id=b.business_id AND p.name='QA Agenda Profesional activo'
 AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.staff_member_id=p.id);
COMMIT;
