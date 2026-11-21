-- Existing appointments keep NULL ownership, including profiles linked after guest booking.
ALTER TABLE appointments
    ADD COLUMN owner_user_id BIGINT,
    ADD CONSTRAINT fk_appointments_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id);

ALTER TABLE customers
    ADD CONSTRAINT uq_customers_business_id_user UNIQUE (business_id, id, user_id);

ALTER TABLE appointments
    ADD CONSTRAINT fk_appointments_owner_profile
        FOREIGN KEY (business_id, customer_id, owner_user_id)
        REFERENCES customers (business_id, id, user_id)
        MATCH SIMPLE;

CREATE INDEX idx_appointments_customer_owner
    ON appointments (business_id, owner_user_id, starts_at DESC, id ASC)
    WHERE owner_user_id IS NOT NULL;
