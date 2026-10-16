-- V1000 inserts explicit IDs without advancing their BIGSERIAL sequences.
-- Keep this repair in local seeds and never rewind a sequence already ahead.
LOCK TABLE businesses, users, staff_members, service_offerings IN SHARE ROW EXCLUSIVE MODE;

SELECT setval(pg_get_serial_sequence('businesses', 'id'),
    GREATEST((SELECT MAX(id) FROM businesses), (SELECT last_value FROM businesses_id_seq)), true);
SELECT setval(pg_get_serial_sequence('users', 'id'),
    GREATEST((SELECT MAX(id) FROM users), (SELECT last_value FROM users_id_seq)), true);
SELECT setval(pg_get_serial_sequence('staff_members', 'id'),
    GREATEST((SELECT MAX(id) FROM staff_members), (SELECT last_value FROM staff_members_id_seq)), true);
SELECT setval(pg_get_serial_sequence('service_offerings', 'id'),
    GREATEST((SELECT MAX(id) FROM service_offerings), (SELECT last_value FROM service_offerings_id_seq)), true);
