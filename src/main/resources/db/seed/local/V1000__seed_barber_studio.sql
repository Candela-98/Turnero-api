INSERT INTO businesses (
    id, name, slug, industry, email, phone, address, timezone,
    status, onboarding_status, created_at, updated_at
) VALUES (
    1, 'Barber Studio', 'barber-studio', 'Barbería premium',
    'hola@barberstudio.demo', '+54 11 1234-5678',
    'Pte. Peron 123, Buenos Aires', 'America/Argentina/Buenos_Aires',
    'ACTIVE', 'READY', NOW(), NOW()
);

INSERT INTO users (
    id, business_id, name, email, auth_provider, auth_subject,
    role, avatar_url, created_at, updated_at
) VALUES (
    1, 1, 'Mateo Ruiz', 'mateo@barberstudio.demo',
    'GOOGLE', 'google-demo-owner-mateo-ruiz',
    'OWNER', NULL, NOW(), NOW()
);

INSERT INTO staff_members (
    id, business_id, user_id, name, role_label, specialty,
    avatar_url, status, created_at, updated_at
) VALUES
    (1, 1, 1, 'Mateo Ruiz', 'Barbero', 'Cortes clásicos y barba', NULL, 'ACTIVE', NOW(), NOW()),
    (2, 1, NULL, 'Lucas Fernández', 'Barbero', 'Fade, barba y perfilado', NULL, 'ACTIVE', NOW(), NOW());

INSERT INTO service_offerings (
    id, business_id, name, category, duration_minutes, price_cents,
    status, created_at, updated_at
) VALUES
    (1, 1, 'Corte clásico', 'Corte', 30, 800000, 'ACTIVE', NOW(), NOW()),
    (2, 1, 'Barba', 'Barba', 30, 600000, 'ACTIVE', NOW(), NOW()),
    (3, 1, 'Corte + barba', 'Combo', 60, 1300000, 'ACTIVE', NOW(), NOW());

INSERT INTO staff_service_offerings (
    staff_member_id, service_offering_id
) VALUES
    (1, 1),
    (1, 2),
    (1, 3),
    (2, 1),
    (2, 2),
    (2, 3);

INSERT INTO business_hours (
    business_id, day_of_week, opens_at, closes_at, is_closed
) VALUES
    (1, 'MONDAY', NULL, NULL, true),
    (1, 'TUESDAY', '09:00', '18:00', false),
    (1, 'WEDNESDAY', '09:00', '18:00', false),
    (1, 'THURSDAY', '09:00', '18:00', false),
    (1, 'FRIDAY', '09:00', '18:00', false),
    (1, 'SATURDAY', '09:00', '18:00', false),
    (1, 'SUNDAY', NULL, NULL, true);

INSERT INTO staff_working_hours (
    staff_member_id, day_of_week, starts_at, ends_at, is_available
) VALUES
    (1, 'MONDAY', NULL, NULL, false),
    (1, 'TUESDAY', '09:00', '18:00', true),
    (1, 'WEDNESDAY', '09:00', '18:00', true),
    (1, 'THURSDAY', '09:00', '18:00', true),
    (1, 'FRIDAY', '09:00', '18:00', true),
    (1, 'SATURDAY', '09:00', '18:00', true),
    (1, 'SUNDAY', NULL, NULL, false),

    (2, 'MONDAY', NULL, NULL, false),
    (2, 'TUESDAY', '10:00', '17:00', true),
    (2, 'WEDNESDAY', '10:00', '17:00', true),
    (2, 'THURSDAY', '10:00', '17:00', true),
    (2, 'FRIDAY', '10:00', '17:00', true),
    (2, 'SATURDAY', '09:00', '14:00', true),
    (2, 'SUNDAY', NULL, NULL, false);

INSERT INTO booking_settings (
    business_id, public_booking_enabled, requires_customer_login,
    booking_window_days, min_notice_hours, cancellation_notice_hours,
    slot_interval_minutes, manual_confirmation_enabled,
    whatsapp_reminders_enabled, created_at, updated_at
) VALUES (
    1, true, false,
    7, 3, 3,
    30, true,
    false, NOW(), NOW()
);