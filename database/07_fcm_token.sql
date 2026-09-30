ALTER TABLE identities ADD COLUMN IF NOT EXISTS fcm_token TEXT;
CREATE INDEX IF NOT EXISTS idx_identities_fcm_token ON identities(fcm_token) WHERE fcm_token IS NOT NULL;
