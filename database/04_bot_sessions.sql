CREATE TABLE IF NOT EXISTS bot_sessions (
    identity_id UUID PRIMARY KEY REFERENCES identities(id) ON DELETE CASCADE,
    current_state VARCHAR(30) DEFAULT 'IDLE',
    selected_group_id UUID REFERENCES groups(id),
    pairing_code VARCHAR(10),
    pairing_expires_at TIMESTAMPTZ,
    context_data JSONB DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
