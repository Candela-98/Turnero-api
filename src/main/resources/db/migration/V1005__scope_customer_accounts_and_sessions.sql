-- User remains the global identity. business_id is the internal access context.
ALTER TABLE users ALTER COLUMN business_id DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT chk_users_internal_business
    CHECK (role = 'CUSTOMER' OR business_id IS NOT NULL);

-- The same global identity can have one customer profile in each business.
ALTER TABLE customers DROP CONSTRAINT customers_user_id_key;
ALTER TABLE customers ADD CONSTRAINT uq_customers_business_user UNIQUE (business_id, user_id);
ALTER TABLE customers ADD CONSTRAINT uq_customers_id_user UNIQUE (id, user_id);

ALTER TABLE user_sessions
    ADD COLUMN scope VARCHAR(8) NOT NULL DEFAULT 'ADMIN',
    ADD COLUMN customer_id BIGINT,
    ADD CONSTRAINT chk_user_sessions_scope CHECK (scope IN ('ADMIN', 'CUSTOMER')),
    ADD CONSTRAINT chk_user_sessions_customer CHECK (
        (scope = 'ADMIN' AND customer_id IS NULL)
        OR (scope = 'CUSTOMER' AND customer_id IS NOT NULL)
    ),
    ADD CONSTRAINT fk_user_sessions_customer_user
        FOREIGN KEY (customer_id, user_id) REFERENCES customers (id, user_id);

ALTER TABLE user_sessions ALTER COLUMN scope DROP DEFAULT;
