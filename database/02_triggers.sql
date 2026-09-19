-- SusuLedger Cryptographic & Financial Balance Triggers

-- 1. Cryptographic Payment Hash Chaining (SHA-256)
CREATE OR REPLACE FUNCTION generate_payment_hash()
RETURNS TRIGGER AS $$
DECLARE
    last_hash VARCHAR(64);
BEGIN
    -- Fetch the most recent payment hash for chaining
    SELECT current_hash INTO last_hash
    FROM payments
    ORDER BY confirmed_at DESC
    LIMIT 1;

    -- Genesis hash anchor if no previous record exists
    IF last_hash IS NULL THEN
        last_hash := '0000000000000000000000000000000000000000000000000000000000000000';
    END IF;

    NEW.prev_hash := last_hash;
    NEW.current_hash := encode(
        digest(
            CONCAT(NEW.id, NEW.cycle_id, NEW.member_id, NEW.amount_paid, NEW.idempotency_key, last_hash),
            'sha256'
        ),
        'hex'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cryptographic_payment_hash ON payments;
CREATE TRIGGER trg_cryptographic_payment_hash
BEFORE INSERT ON payments
FOR EACH ROW EXECUTE FUNCTION generate_payment_hash();

-- 2. Audit Trail Logger Trigger on Financial Corrections
CREATE OR REPLACE FUNCTION log_ledger_correction_audit()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO audit_log (actor_id, action, payload)
    VALUES (
        NEW.entered_by,
        'LEDGER_CORRECTION',
        json_build_object(
            'payment_id', NEW.payment_id,
            'action', NEW.action,
            'reason', NEW.reason,
            'approved_by', NEW.approved_by
        )::jsonb
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_log_ledger_correction ON ledger_corrections;
CREATE TRIGGER trg_log_ledger_correction
AFTER INSERT ON ledger_corrections
FOR EACH ROW EXECUTE FUNCTION log_ledger_correction_audit();
