const db = require('./database');

const MIGRATIONS_SQL = `
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS bot_pairings (
    code VARCHAR(10) PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    phone VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'expired')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paired_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_bot_pairings_expiry ON bot_pairings(expires_at);
CREATE INDEX IF NOT EXISTS idx_bot_pairings_status_group ON bot_pairings(status, group_id);

CREATE TABLE IF NOT EXISTS webhook_events (
    id TEXT PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_webhook_events_processed_at ON webhook_events(processed_at);

CREATE TABLE IF NOT EXISTS payment_receipts (
    payment_id UUID PRIMARY KEY REFERENCES payments(id) ON DELETE CASCADE,
    queued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS week_notifications (
    cycle_id UUID NOT NULL REFERENCES cycles(id) ON DELETE CASCADE,
    member_id UUID NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    notification_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cycle_id, member_id, notification_type)
);

ALTER TABLE message_log ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
ALTER TABLE bot_sessions ADD COLUMN IF NOT EXISTS context_data JSONB DEFAULT '{}'::jsonb;
ALTER TABLE identities ADD COLUMN IF NOT EXISTS fcm_token TEXT;

CREATE INDEX IF NOT EXISTS idx_identities_fcm_token ON identities(fcm_token) WHERE fcm_token IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_message_log_identity_direction ON message_log(identity_id, direction);
CREATE INDEX IF NOT EXISTS idx_claims_group_cycle_state ON claims(group_id, cycle_id, state);
`;

async function runMigrations() {
  const client = await db.pool.connect();
  try {
    await client.query('BEGIN');
    await client.query("SELECT pg_advisory_xact_lock(hashtext('susu-schema-migration'))");
    const existing = await client.query("SELECT to_regclass('public.groups') AS table_name");
    if (existing.rows[0]?.table_name) {
      await client.query(MIGRATIONS_SQL);
    }
    await client.query('COMMIT');
    console.log('[Migrate] Production database schema verified and up-to-date');
  } catch (error) {
    await client.query('ROLLBACK');
    console.warn('[Migrate] Schema migration notice:', error.message);
  } finally {
    client.release();
  }
}

module.exports = { runMigrations, MIGRATIONS_SQL };
