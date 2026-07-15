-- Remove in-app payment storage; payments are handled outside this system.

ALTER TABLE bookings DROP COLUMN IF EXISTS payment_id;

DROP TABLE IF EXISTS payments;
