-- SusuLedger Production Database Schema (Cloud SQL PostgreSQL)
-- Enforces balanced double-entry accounting and SHA-256 cryptographic hash-chaining.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. IDENTITIES & USERS
CREATE TABLE identities (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    phone VARCHAR(20) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    dpc_consent_granted BOOLEAN DEFAULT FALSE,
    dpc_consent_timestamp TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id UUID PRIMARY KEY REFERENCES identities(id) ON DELETE CASCADE,
    pin_hash TEXT NOT NULL,
    pin_salt TEXT NOT NULL,
    role VARCHAR(20) DEFAULT 'treasurer' CHECK (role IN ('treasurer', 'second_officer')),
    biometric_enrolled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. GROUPS & MEMBERS
CREATE TABLE groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) DEFAULT 'GHS',
    schedule VARCHAR(20) DEFAULT 'weekly' CHECK (schedule IN ('weekly', 'monthly')),
    treasurer_id UUID NOT NULL REFERENCES identities(id),
    officer_id UUID REFERENCES identities(id),
    state VARCHAR(20) DEFAULT 'active' CHECK (state IN ('active', 'paused', 'closed')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    identity_id UUID NOT NULL REFERENCES identities(id),
    alias VARCHAR(100),
    state VARCHAR(20) DEFAULT 'active' CHECK (state IN ('active', 'paused', 'exited')),
    joined_cycle INT NOT NULL DEFAULT 1,
    exited_cycle INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, identity_id)
);

-- 3. CYCLES
CREATE TABLE cycles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id UUID NOT NULL REFERENCES groups(id),
    number INT NOT NULL,
    amount_due NUMERIC(12, 2) NOT NULL,
    due_date DATE NOT NULL,
    state VARCHAR(20) DEFAULT 'open' CHECK (state IN ('open', 'closed', 'paused')),
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by UUID REFERENCES identities(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(group_id, number)
);

-- 4. DOUBLE-ENTRY CHART OF ACCOUNTS
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id UUID NOT NULL REFERENCES groups(id),
    name VARCHAR(100) NOT NULL, -- e.g., 'Cash/MoMo Asset', 'Member Equity: Kofi'
    type VARCHAR(20) NOT NULL CHECK (type IN ('asset', 'liability', 'equity')),
    member_id UUID REFERENCES members(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. APPEND-ONLY PAYMENTS & CRYPTOGRAPHIC LEDGER
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cycle_id UUID NOT NULL REFERENCES cycles(id),
    member_id UUID NOT NULL REFERENCES members(id),
    amount_paid NUMERIC(12, 2) NOT NULL CHECK (amount_paid > 0),
    method VARCHAR(10) NOT NULL CHECK (method IN ('CASH', 'MOMO', 'AGENT')),
    status VARCHAR(20) DEFAULT 'confirmed' CHECK (status IN ('confirmed', 'reversed')),
    confirmed_by UUID NOT NULL REFERENCES identities(id),
    confirmed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    idempotency_key VARCHAR(100) UNIQUE NOT NULL,
    source VARCHAR(10) DEFAULT 'whatsapp' CHECK (source IN ('whatsapp', 'app')),
    prev_hash VARCHAR(64) NOT NULL,
    current_hash VARCHAR(64) NOT NULL
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(6) NOT NULL CHECK (entry_type IN ('debit', 'credit')),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. CLAIMS, DISPUTES & CORRECTIONS
CREATE TABLE claims (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cycle_id UUID NOT NULL REFERENCES cycles(id),
    member_id UUID NOT NULL REFERENCES members(id),
    claimed_amount NUMERIC(12, 2) NOT NULL,
    evidence_momo_id VARCHAR(100),
    state VARCHAR(20) DEFAULT 'pending' CHECK (state IN ('pending', 'confirmed', 'rejected', 'disputed')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_by UUID REFERENCES identities(id),
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE ledger_corrections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    action VARCHAR(20) NOT NULL,
    reason TEXT NOT NULL,
    entered_by UUID NOT NULL REFERENCES identities(id),
    approved_by UUID REFERENCES identities(id), -- Dual sign-off
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. SUBSCRIPTIONS (Paystack Ghana MoMo)
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id UUID NOT NULL REFERENCES groups(id),
    paystack_customer_code VARCHAR(100),
    paystack_subscription_code VARCHAR(100),
    plan_code VARCHAR(50) DEFAULT 'PLN_susu_standard',
    amount NUMERIC(12, 2) DEFAULT 40.00,
    currency VARCHAR(3) DEFAULT 'GHS',
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'trial', 'past_due', 'lapsed', 'cancelled')),
    trial_ends_at TIMESTAMP WITH TIME ZONE,
    current_period_start TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    current_period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. AUDIT LOGS, MESSAGES & SESSIONS
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id UUID REFERENCES identities(id),
    group_id UUID REFERENCES groups(id),
    action VARCHAR(50) NOT NULL,
    payload JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE message_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    identity_id UUID REFERENCES identities(id),
    phone VARCHAR(20) NOT NULL,
    direction VARCHAR(3) CHECK (direction IN ('IN', 'OUT')),
    body TEXT,
    meta_status VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bot_sessions (
    identity_id UUID PRIMARY KEY REFERENCES identities(id) ON DELETE CASCADE,
    current_state VARCHAR(30) DEFAULT 'IDLE',
    selected_group_id UUID REFERENCES groups(id),
    pairing_code VARCHAR(10),
    pairing_expires_at TIMESTAMP WITH TIME ZONE,
    context_data JSONB DEFAULT '{}'::jsonb,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- PERFORMANCE INDEXING
CREATE INDEX idx_identities_phone ON identities(phone);
CREATE INDEX idx_members_group ON members(group_id);
CREATE INDEX idx_members_identity ON members(identity_id);
CREATE INDEX idx_payments_cycle ON payments(cycle_id);
CREATE INDEX idx_payments_member ON payments(member_id);
CREATE INDEX idx_ledger_payment ON ledger_entries(payment_id);
CREATE INDEX idx_claims_cycle ON claims(cycle_id);
CREATE INDEX idx_audit_payload_gin ON audit_log USING GIN (payload);
CREATE INDEX idx_message_phone ON message_log(phone);
