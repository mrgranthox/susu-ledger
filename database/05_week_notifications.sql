CREATE TABLE IF NOT EXISTS week_notifications (
  cycle_id UUID NOT NULL REFERENCES cycles(id),
  member_id UUID NOT NULL REFERENCES members(id),
  state VARCHAR(16) NOT NULL DEFAULT 'pending',
  attempts INT NOT NULL DEFAULT 0,
  accepted_at TIMESTAMPTZ,
  PRIMARY KEY(cycle_id,member_id)
);
