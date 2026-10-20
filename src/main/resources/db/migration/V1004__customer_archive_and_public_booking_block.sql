-- INACTIVE keeps its stored value and now means archived; no data or appointment is removed.
ALTER TABLE customers ADD COLUMN public_booking_blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN public_booking_block_reason VARCHAR(500);
ALTER TABLE customers ADD CONSTRAINT chk_customer_booking_block_reason CHECK (
  (public_booking_blocked AND public_booking_block_reason IS NOT NULL
    AND length(trim(public_booking_block_reason)) BETWEEN 1 AND 500)
  OR (NOT public_booking_blocked AND public_booking_block_reason IS NULL)
);
-- Full contact indexes also serve identity reuse, which includes archived and unblocked records.
CREATE INDEX idx_customers_business_normalized_email ON customers (business_id, lower(trim(email)));
CREATE INDEX idx_customers_business_normalized_phone ON customers
  (business_id, regexp_replace(coalesce(phone_number, ''), '[^0-9]', '', 'g'));
CREATE INDEX idx_customers_business_archive_block ON customers (business_id, status, public_booking_blocked);
