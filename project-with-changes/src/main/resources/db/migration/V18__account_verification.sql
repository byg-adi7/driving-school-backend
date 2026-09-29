-- ============================================================
-- users: login now requires a verified account - verified by a one-time code
-- sent either to the account's email (email_verified) or to the WhatsApp
-- number on its profile (phone_verified). Either one verifies the account.
-- ============================================================
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Every account that exists before verification became mandatory keeps
-- working as-is: only accounts created from now on have to verify.
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
