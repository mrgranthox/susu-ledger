CREATE INDEX IF NOT EXISTS idx_bot_pairings_status_group ON bot_pairings(status, group_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_processed_at ON webhook_events(processed_at);
