CREATE TABLE businesses(
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  slug VARCHAR(120) NOT NULL UNIQUE,
  industry VARCHAR(120),
  email VARCHAR(150),
  phone VARCHAR(50),
  address VARCHAR(255),
  timezone VARCHAR(80) NOT NULL,
  status VARCHAR(30) NOT NULL,
  onboarding_status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,

  CONSTRAINT chk_businesses_status
      CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),

  CONSTRAINT chk_businesses_onboarding_status
      CHECK (onboarding_status IN ('PENDING_SETUP', 'READY'))
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL,
    auth_provider VARCHAR(30) NOT NULL,
    auth_subject VARCHAR(180) NOT NULL,
    role VARCHAR(30) NOT NULL,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_users_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT uq_users_auth
        UNIQUE (auth_provider, auth_subject),

    CONSTRAINT chk_users_auth_provider
        CHECK (auth_provider IN ('GOOGLE')),

    CONSTRAINT chk_users_role
            CHECK (role IN ('OWNER', 'ADMIN', 'RECEPTIONIST', 'STAFF', 'CUSTOMER'))
);

CREATE TABLE user_sessions (
      id BIGSERIAL PRIMARY KEY,
      user_id BIGINT NOT NULL,
      session_token_hash VARCHAR(255) NOT NULL UNIQUE,
      created_at TIMESTAMP NOT NULL,
      expires_at TIMESTAMP NOT NULL,
      last_seen_at TIMESTAMP NOT NULL,
      revoked_at TIMESTAMP,
      ip_address VARCHAR(80),
      user_agent VARCHAR(500),

   CONSTRAINT fk_user_sessions_user
       FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    user_id BIGINT UNIQUE,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(150),
    phone_number VARCHAR(50),
    status VARCHAR(30) NOT NULL,
    internal_notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_customers_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT fk_customers_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT chk_customers_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE staff_members (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    user_id BIGINT UNIQUE,
    name VARCHAR(120) NOT NULL,
    role_label VARCHAR(80),
    specialty VARCHAR(150),
    avatar_url VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_staff_members_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT fk_staff_members_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT chk_staff_members_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE service_offerings (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(80),
    duration_minutes INTEGER NOT NULL,
    price_cents INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_service_offerings_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT chk_service_offerings_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    CONSTRAINT chk_service_offerings_duration
        CHECK (duration_minutes > 0),

    CONSTRAINT chk_service_offerings_price
        CHECK (price_cents >= 0)
);

CREATE TABLE staff_service_offerings (
    id BIGSERIAL PRIMARY KEY,
    staff_member_id BIGINT NOT NULL,
    service_offering_id BIGINT NOT NULL,

    CONSTRAINT fk_staff_service_offerings_staff
        FOREIGN KEY (staff_member_id) REFERENCES staff_members(id),

    CONSTRAINT fk_staff_service_offerings_service
        FOREIGN KEY (service_offering_id) REFERENCES service_offerings(id),

    CONSTRAINT uq_staff_service_offering
        UNIQUE (staff_member_id, service_offering_id)
);

CREATE TABLE appointments (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    service_offering_id BIGINT NOT NULL,
    staff_member_id BIGINT NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    duration_minutes INTEGER NOT NULL,
    price_cents INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL,
    customer_notes TEXT,
    internal_notes TEXT,
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_appointments_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT fk_appointments_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id),

    CONSTRAINT fk_appointments_service_offering
        FOREIGN KEY (service_offering_id) REFERENCES service_offerings(id),

    CONSTRAINT fk_appointments_staff_member
        FOREIGN KEY (staff_member_id) REFERENCES staff_members(id),

    CONSTRAINT chk_appointments_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),

    CONSTRAINT chk_appointments_source
        CHECK (source IN ('ADMIN', 'PUBLIC_BOOKING')),

    CONSTRAINT chk_appointments_duration
        CHECK (duration_minutes > 0),

    CONSTRAINT chk_appointments_price
        CHECK (price_cents >= 0),

    CONSTRAINT chk_appointments_time_range
        CHECK (ends_at > starts_at)
);

CREATE TABLE appointment_public_tokens (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_appointment_public_tokens_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id),

    CONSTRAINT chk_appointment_public_tokens_type
        CHECK (type IN ('CANCEL'))
);

CREATE TABLE business_hours (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    opens_at TIME,
    closes_at TIME,
    is_closed BOOLEAN NOT NULL,

    CONSTRAINT fk_business_hours_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT chk_business_hours_day
        CHECK (day_of_week IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
            'FRIDAY', 'SATURDAY', 'SUNDAY'
        )),

    CONSTRAINT chk_business_hours_time_range
        CHECK (
            is_closed = true
            OR (opens_at IS NOT NULL AND closes_at IS NOT NULL AND closes_at > opens_at)
        ),

    CONSTRAINT uq_business_hours_day
        UNIQUE (business_id, day_of_week)
);

CREATE TABLE staff_working_hours (
    id BIGSERIAL PRIMARY KEY,
    staff_member_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    starts_at TIME,
    ends_at TIME,
    is_available BOOLEAN NOT NULL,

    CONSTRAINT fk_staff_working_hours_staff
        FOREIGN KEY (staff_member_id) REFERENCES staff_members(id),

    CONSTRAINT chk_staff_working_hours_day
        CHECK (day_of_week IN (
            'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
            'FRIDAY', 'SATURDAY', 'SUNDAY'
        )),

    CONSTRAINT chk_staff_working_hours_time_range
        CHECK (
            is_available = false
            OR (starts_at IS NOT NULL AND ends_at IS NOT NULL AND ends_at > starts_at)
        ),

    CONSTRAINT uq_staff_working_hours_day
        UNIQUE (staff_member_id, day_of_week)
);

CREATE TABLE availability_exceptions (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    staff_member_id BIGINT,
    date DATE NOT NULL,
    starts_at TIME,
    ends_at TIME,
    type VARCHAR(30) NOT NULL,
    reason VARCHAR(255),

    CONSTRAINT fk_availability_exceptions_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT fk_availability_exceptions_staff
        FOREIGN KEY (staff_member_id) REFERENCES staff_members(id),

    CONSTRAINT chk_availability_exceptions_type
        CHECK (type IN ('CLOSED', 'SPECIAL_HOURS', 'BLOCKED')),

    CONSTRAINT chk_availability_exceptions_time_range
        CHECK (
            type = 'CLOSED'
            OR (starts_at IS NOT NULL AND ends_at IS NOT NULL AND ends_at > starts_at)
        )
);

CREATE TABLE booking_settings (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL UNIQUE,
    public_booking_enabled BOOLEAN NOT NULL,
    requires_customer_login BOOLEAN NOT NULL,
    booking_window_days INTEGER NOT NULL,
    min_notice_hours INTEGER NOT NULL,
    cancellation_notice_hours INTEGER NOT NULL,
    slot_interval_minutes INTEGER NOT NULL,
    manual_confirmation_enabled BOOLEAN NOT NULL,
    whatsapp_reminders_enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_booking_settings_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT chk_booking_settings_booking_window
        CHECK (booking_window_days > 0),

    CONSTRAINT chk_booking_settings_min_notice
        CHECK (min_notice_hours >= 0),

    CONSTRAINT chk_booking_settings_cancellation_notice
        CHECK (cancellation_notice_hours >= 0),

    CONSTRAINT chk_booking_settings_slot_interval
        CHECK (slot_interval_minutes > 0)
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL,
    appointment_id BIGINT,
    customer_id BIGINT,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_notifications_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),

    CONSTRAINT fk_notifications_appointment
        FOREIGN KEY (appointment_id) REFERENCES appointments(id),

    CONSTRAINT fk_notifications_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id),

    CONSTRAINT chk_notifications_status
        CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),

    CONSTRAINT chk_notifications_type
        CHECK (type IN (
            'APPOINTMENT_PENDING',
            'APPOINTMENT_CONFIRMED',
            'APPOINTMENT_CANCELLED',
            'APPOINTMENT_RESCHEDULED',
            'BOOKING_ERROR'
        ))
);

CREATE INDEX idx_users_business_id ON users(business_id);
CREATE INDEX idx_customers_business_id ON customers(business_id);
CREATE INDEX idx_staff_members_business_id ON staff_members(business_id);
CREATE INDEX idx_service_offerings_business_id ON service_offerings(business_id);

CREATE INDEX idx_appointments_business_starts_at
    ON appointments(business_id, starts_at);

CREATE INDEX idx_appointments_staff_starts_at
    ON appointments(staff_member_id, starts_at);

CREATE INDEX idx_appointments_customer_id
    ON appointments(customer_id);

CREATE INDEX idx_user_sessions_user_id
    ON user_sessions(user_id);

CREATE INDEX idx_appointment_public_tokens_appointment_id
    ON appointment_public_tokens(appointment_id);

CREATE INDEX idx_availability_exceptions_business_date
    ON availability_exceptions(business_id, date);

CREATE INDEX idx_availability_exceptions_staff_date
    ON availability_exceptions(staff_member_id, date);

CREATE INDEX idx_notifications_business_created_at
    ON notifications(business_id, created_at);