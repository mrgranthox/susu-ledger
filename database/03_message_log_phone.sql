ALTER TABLE message_log ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
UPDATE message_log m SET phone=i.phone
FROM identities i WHERE m.identity_id=i.id AND m.phone IS NULL;
CREATE INDEX IF NOT EXISTS idx_message_phone ON message_log(phone);
