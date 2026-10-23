-- Solo DB LOCAL. Reprograma exclusivamente los fixtures TURN-90/70 usando horarios y catálogo existentes.
-- psql "$DATABASE_URL" -v business_id=1 -f scripts/schedule-local-agenda.sql
-- Historial terminal en pasado; pendientes/confirmados futuros con profesionales activos.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE agenda_schedule_context AS
SELECT b.id AS business_id, now() AT TIME ZONE b.timezone AS local_now,
 coalesce(s.slot_interval_minutes,30) AS slot_minutes
FROM businesses b LEFT JOIN booking_settings s ON s.business_id=b.id WHERE b.id=:'business_id'::bigint;
CREATE TEMP TABLE agenda_schedule_fixtures AS
SELECT a.* FROM appointments a JOIN agenda_schedule_context b ON b.business_id=a.business_id
WHERE a.customer_notes='Fixture local TURN-90/70';
CREATE TEMP TABLE agenda_schedule_candidates AS
SELECT p.id AS staff_id, slot.starts_at, day.value+least(bh.closes_at,sh.ends_at) AS window_end
FROM agenda_schedule_context b
JOIN staff_members p ON p.business_id=b.business_id AND p.name NOT LIKE 'QA Agenda%'
CROSS JOIN LATERAL generate_series(b.local_now::date-interval '30 days',b.local_now::date+interval '30 days',interval '1 day') AS day(value)
JOIN business_hours bh ON bh.business_id=b.business_id
 AND bh.day_of_week=(ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'])[extract(isodow FROM day.value)::int]
 AND NOT bh.is_closed
JOIN staff_working_hours sh ON sh.staff_member_id=p.id AND sh.day_of_week=bh.day_of_week AND sh.is_available
CROSS JOIN LATERAL generate_series(day.value+greatest(bh.opens_at,sh.starts_at),day.value+least(bh.closes_at,sh.ends_at),make_interval(mins=>b.slot_minutes)) AS slot(starts_at);
CREATE TEMP TABLE agenda_schedule_plan (
 id bigint PRIMARY KEY, staff_id bigint NOT NULL, customer_id bigint NOT NULL,
 service_id bigint NOT NULL, starts_at timestamp NOT NULL, ends_at timestamp NOT NULL,
 duration_minutes int NOT NULL, price_cents int NOT NULL
);
DO $$
DECLARE
 ctx record; f record; member record; service record; picked record;
 historic_used boolean := false; historic_id bigint; active_count int;
BEGIN
 SELECT * INTO STRICT ctx FROM agenda_schedule_context;
 IF NOT EXISTS (SELECT 1 FROM agenda_schedule_fixtures) THEN RAISE EXCEPTION 'No local agenda fixtures found'; END IF;
 SELECT count(*) INTO active_count FROM staff_members p WHERE p.business_id=ctx.business_id AND p.status='ACTIVE' AND p.deleted_at IS NULL
  AND p.name NOT LIKE 'QA Agenda%' AND EXISTS(SELECT 1 FROM agenda_schedule_candidates c WHERE c.staff_id=p.id)
  AND EXISTS(SELECT 1 FROM staff_service_offerings r JOIN service_offerings s ON s.id=r.service_offering_id WHERE r.staff_member_id=p.id AND s.business_id=ctx.business_id AND s.status='ACTIVE' AND s.deleted_at IS NULL);
 IF active_count=0 THEN RAISE EXCEPTION 'No active professionals with working hours and services'; END IF;
 SELECT p.id INTO historic_id FROM staff_members p WHERE p.business_id=ctx.business_id AND p.deleted_at IS NOT NULL AND p.name NOT LIKE 'QA Agenda%'
  AND EXISTS(SELECT 1 FROM agenda_schedule_candidates c WHERE c.staff_id=p.id)
  AND EXISTS(SELECT 1 FROM staff_service_offerings r JOIN service_offerings s ON s.id=r.service_offering_id WHERE r.staff_member_id=p.id AND s.business_id=ctx.business_id AND s.status='ACTIVE' AND s.deleted_at IS NULL)
 ORDER BY p.id LIMIT 1;
 FOR f IN SELECT * FROM agenda_schedule_fixtures ORDER BY (status IN ('PENDING','CONFIRMED')),id LOOP
  SELECT * INTO STRICT member FROM staff_members WHERE id=f.staff_member_id AND business_id=ctx.business_id;
  IF f.status IN ('PENDING','CONFIRMED') AND (member.status<>'ACTIVE' OR member.deleted_at IS NOT NULL) THEN
   SELECT p.* INTO STRICT member FROM staff_members p WHERE p.business_id=ctx.business_id AND p.status='ACTIVE' AND p.deleted_at IS NULL
    AND p.name NOT LIKE 'QA Agenda%' AND EXISTS(SELECT 1 FROM agenda_schedule_candidates c WHERE c.staff_id=p.id)
    AND EXISTS(SELECT 1 FROM staff_service_offerings r JOIN service_offerings s ON s.id=r.service_offering_id WHERE r.staff_member_id=p.id AND s.business_id=ctx.business_id AND s.status='ACTIVE' AND s.deleted_at IS NULL)
   ORDER BY p.id LIMIT 1 OFFSET f.id%active_count;
  ELSIF f.status NOT IN ('PENDING','CONFIRMED') AND NOT historic_used AND historic_id IS NOT NULL THEN
   SELECT * INTO STRICT member FROM staff_members WHERE id=historic_id;
   historic_used := true;
  END IF;
  SELECT s.* INTO STRICT service FROM service_offerings s JOIN staff_service_offerings r ON r.service_offering_id=s.id
   WHERE r.staff_member_id=member.id AND s.business_id=ctx.business_id AND s.status='ACTIVE' AND s.deleted_at IS NULL
   ORDER BY (s.id=f.service_offering_id) DESC,s.id LIMIT 1;
  SELECT c.starts_at,c.starts_at+make_interval(mins=>service.duration_minutes) AS ends_at INTO picked
  FROM agenda_schedule_candidates c
  WHERE c.staff_id=member.id AND c.starts_at+make_interval(mins=>service.duration_minutes)<=c.window_end
   AND CASE WHEN f.status IN ('PENDING','CONFIRMED') THEN c.starts_at>ctx.local_now+interval '15 minutes'
            ELSE c.starts_at+make_interval(mins=>service.duration_minutes)<=ctx.local_now END
   AND NOT EXISTS (SELECT 1 FROM availability_exceptions e WHERE e.business_id=ctx.business_id
    AND (e.staff_member_id IS NULL OR e.staff_member_id=member.id) AND e.type='BLOCKED' AND e.date=c.starts_at::date
    AND (e.starts_at IS NULL OR e.ends_at IS NULL OR (c.starts_at<e.date+e.ends_at AND c.starts_at+make_interval(mins=>service.duration_minutes)>e.date+e.starts_at)))
   AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.business_id=ctx.business_id
    AND (a.staff_member_id=member.id OR a.customer_id=f.customer_id)
    AND NOT EXISTS (SELECT 1 FROM agenda_schedule_fixtures x WHERE x.id=a.id)
    AND c.starts_at<a.ends_at AND c.starts_at+make_interval(mins=>service.duration_minutes)>a.starts_at)
   AND NOT EXISTS (SELECT 1 FROM agenda_schedule_plan a WHERE (a.staff_id=member.id OR a.customer_id=f.customer_id)
    AND c.starts_at<a.ends_at AND c.starts_at+make_interval(mins=>service.duration_minutes)>a.starts_at)
  ORDER BY
   CASE WHEN f.status IN ('PENDING','CONFIRMED') THEN c.starts_at END ASC,
   CASE WHEN f.status NOT IN ('PENDING','CONFIRMED') THEN c.starts_at::date END DESC,
   c.starts_at ASC LIMIT 1;
  IF NOT FOUND THEN RAISE EXCEPTION 'No valid slot for fixture % (professional %)',f.id,member.id; END IF;
  INSERT INTO agenda_schedule_plan VALUES(f.id,member.id,f.customer_id,service.id,picked.starts_at,picked.ends_at,service.duration_minutes,service.price_cents);
 END LOOP;
END $$;
UPDATE appointments a SET staff_member_id=p.staff_id,service_offering_id=p.service_id,starts_at=p.starts_at,ends_at=p.ends_at,
 duration_minutes=p.duration_minutes,price_cents=p.price_cents,created_at=least(a.created_at,p.starts_at-interval '1 day'),updated_at=now()
FROM agenda_schedule_plan p WHERE p.id=a.id;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM appointments a JOIN appointments b ON a.business_id=b.business_id AND a.id<b.id
  AND (a.staff_member_id=b.staff_member_id OR a.customer_id=b.customer_id)
  AND a.starts_at<b.ends_at AND a.ends_at>b.starts_at
  WHERE a.business_id=(SELECT business_id FROM agenda_schedule_context)
  AND (EXISTS(SELECT 1 FROM agenda_schedule_fixtures f WHERE f.id=a.id) OR EXISTS(SELECT 1 FROM agenda_schedule_fixtures f WHERE f.id=b.id)))
 THEN RAISE EXCEPTION 'Overlapping fixtures detected'; END IF;
END $$;
SELECT starts_at::date AS day,status,count(*) AS appointments FROM appointments
WHERE id IN (SELECT id FROM agenda_schedule_fixtures) GROUP BY 1,2 ORDER BY 1,2;
COMMIT;
