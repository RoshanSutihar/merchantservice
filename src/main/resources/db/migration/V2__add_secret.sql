ALTER TABLE merchants
ADD COLUMN IF NOT EXISTS secret_key TEXT;

COMMENT ON COLUMN merchants.secret_key IS 'Secret key (API/webhook secret) provided by the payment core system. Highly sensitive – treat like a password.';

CREATE INDEX IF NOT EXISTS idx_merchants_secret_key ON merchants(secret_key);