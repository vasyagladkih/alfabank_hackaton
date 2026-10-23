-- Additive bootstrap for both an empty database and the supplied users/applications schema.
-- Flyway baseline-version MUST stay 0: an existing database must also execute this migration.
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(32) NOT NULL,
    company_name VARCHAR(255),
    inn VARCHAR(12),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL,
    term_months INT NOT NULL,
    purpose VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO users (username, role, company_name, inn) VALUES
('client_good', 'CLIENT', 'ООО "Ромашка"', '7701234567'),
('client_bad', 'CLIENT', 'ООО "Однодневка"', '7702999999'),
('bank_employee', 'EMPLOYEE', 'АО "АЛЬФА-БАНК"', '7728168971')
ON CONFLICT (username) DO NOTHING;

ALTER TABLE applications ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE applications ADD COLUMN IF NOT EXISTS demo_scenario_key VARCHAR(80);
ALTER TABLE applications ADD COLUMN IF NOT EXISTS decision_summary TEXT;
ALTER TABLE applications ADD COLUMN IF NOT EXISTS offered_amount NUMERIC(18,2);
ALTER TABLE applications ADD COLUMN IF NOT EXISTS offered_term_months INT;
ALTER TABLE applications ADD COLUMN IF NOT EXISTS offered_annual_rate NUMERIC(7,4);
ALTER TABLE applications ADD COLUMN IF NOT EXISTS offered_monthly_payment NUMERIC(18,2);
-- NOT VALID preserves even externally-written legacy rows; new inserts/updates are checked.
ALTER TABLE applications ADD CONSTRAINT applications_request_amount_bounds
    CHECK (amount BETWEEN 100000 AND 50000000) NOT VALID;
ALTER TABLE applications ADD CONSTRAINT applications_request_term_bounds
    CHECK (term_months BETWEEN 1 AND 60) NOT VALID;
ALTER TABLE applications ADD CONSTRAINT applications_known_status
    CHECK (status IN ('PROCESSING','APPROVED','REJECTED','MANUAL_REVIEW','OFFER_PROPOSED')) NOT VALID;
ALTER TABLE applications ADD CONSTRAINT applications_known_purpose
    CHECK (purpose IN ('WORKING_CAPITAL','EQUIPMENT','BUSINESS_EXPANSION','REFINANCING')) NOT VALID;
ALTER TABLE applications ADD CONSTRAINT applications_version_nonnegative CHECK (version >= 0);
ALTER TABLE applications ADD CONSTRAINT applications_offer_bounds CHECK (
    offered_amount IS NULL OR (offered_amount >= 100000 AND offered_amount <= amount)
);
ALTER TABLE applications ADD CONSTRAINT applications_offer_term CHECK (
    offered_term_months IS NULL OR (offered_term_months BETWEEN 1 AND 60)
);
ALTER TABLE applications ADD CONSTRAINT applications_offer_rate CHECK (
    offered_annual_rate IS NULL OR offered_annual_rate >= 0
);
ALTER TABLE applications ADD CONSTRAINT applications_offer_payment CHECK (
    offered_monthly_payment IS NULL OR offered_monthly_payment >= 0
);
CREATE UNIQUE INDEX applications_demo_scenario_key_uq ON applications(demo_scenario_key)
    WHERE demo_scenario_key IS NOT NULL;
CREATE INDEX applications_owner_created_idx ON applications(user_id, created_at DESC, id DESC);
CREATE INDEX applications_status_created_idx ON applications(status, created_at, id);

-- Sources are entirely synthetic. Nullable facts mean unknown, never false or zero.
CREATE TABLE company_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    business_registered_on DATE,
    account_opened_on DATE,
    director_changed_on DATE,
    sole_founder_changed_on DATE,
    management_history_complete BOOLEAN,
    internal_monthly_debt_rub NUMERIC(18,2) CHECK (internal_monthly_debt_rub >= 0),
    source VARCHAR(64) NOT NULL CHECK (source = 'SYNTHETIC_INTERNAL'),
    source_version VARCHAR(64) NOT NULL DEFAULT 'DEMO_DATA_V1',
    as_of DATE NOT NULL
);

CREATE TABLE monthly_cashflows (
    user_id BIGINT NOT NULL REFERENCES users(id),
    month DATE NOT NULL CHECK (EXTRACT(DAY FROM month) = 1),
    turnover_rub NUMERIC(18,2) CHECK (turnover_rub >= 0),
    source VARCHAR(64) NOT NULL CHECK (source = 'SYNTHETIC_INTERNAL'),
    source_version VARCHAR(64) NOT NULL DEFAULT 'DEMO_DATA_V1',
    as_of DATE NOT NULL,
    PRIMARY KEY (user_id, month)
);

CREATE TABLE synthetic_bureau_facts (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    score INT CHECK (score BETWEEN 0 AND 1000),
    monthly_debt_rub NUMERIC(18,2) CHECK (monthly_debt_rub >= 0),
    max_overdue_days INT CHECK (max_overdue_days >= 0),
    recent_closed_overdue_days INT CHECK (recent_closed_overdue_days >= 0),
    source VARCHAR(64) NOT NULL CHECK (source = 'SYNTHETIC_BKI'),
    source_version VARCHAR(64) NOT NULL DEFAULT 'DEMO_DATA_V1',
    as_of DATE NOT NULL
);

CREATE TABLE synthetic_registry_facts (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    tax_blocked BOOLEAN,
    bankrupt BOOLEAN,
    liquidating BOOLEAN,
    source VARCHAR(64) NOT NULL CHECK (source = 'SYNTHETIC_FNS'),
    source_version VARCHAR(64) NOT NULL DEFAULT 'DEMO_DATA_V1',
    as_of DATE NOT NULL
);

CREATE TABLE application_snapshots (
    application_id BIGINT PRIMARY KEY REFERENCES applications(id),
    schema_version VARCHAR(32) NOT NULL,
    as_of DATE NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    origin VARCHAR(32) NOT NULL CHECK (origin IN ('DEMO_PROFILE', 'DEMO_SCENARIO', 'LEGACY_RECOVERY')),
    payload_json JSONB NOT NULL CHECK (jsonb_typeof(payload_json) = 'object'),
    payload_sha256 CHAR(64) NOT NULL CHECK (payload_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE scoring_results (
    application_id BIGINT PRIMARY KEY REFERENCES applications(id),
    rule_version VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL CHECK (jsonb_typeof(payload_json) = 'object'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE application_audit (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id),
    event_key VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL CHECK (actor_type IN ('SYSTEM', 'CLIENT', 'EMPLOYEE')),
    actor_user_id BIGINT REFERENCES users(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    application_version BIGINT NOT NULL CHECK (application_version >= 0),
    rationale TEXT NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload_json) = 'object'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (application_id, event_key)
);
CREATE INDEX application_audit_application_idx ON application_audit(application_id, created_at, id);

CREATE TABLE mutation_requests (
    application_id BIGINT NOT NULL REFERENCES applications(id),
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    request_id UUID NOT NULL,
    command_sha256 CHAR(64) NOT NULL CHECK (command_sha256 ~ '^[0-9a-f]{64}$'),
    response_json JSONB NOT NULL CHECK (jsonb_typeof(response_json) = 'object'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (application_id, actor_user_id, request_id)
);

CREATE TABLE processing_jobs (
    application_id BIGINT PRIMARY KEY REFERENCES applications(id),
    attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    last_error_code VARCHAR(64)
);
CREATE INDEX processing_jobs_ready_idx ON processing_jobs(available_at, application_id)
    WHERE completed_at IS NULL;
INSERT INTO processing_jobs(application_id)
SELECT id FROM applications WHERE status = 'PROCESSING'
ON CONFLICT (application_id) DO NOTHING;

-- Application code may append evidence, never rewrite the evidence used for a decision.
CREATE FUNCTION reject_demo_evidence_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Evidence records are append-only' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER application_snapshots_immutable BEFORE UPDATE OR DELETE ON application_snapshots
    FOR EACH ROW EXECUTE FUNCTION reject_demo_evidence_mutation();
CREATE TRIGGER scoring_results_immutable BEFORE UPDATE OR DELETE ON scoring_results
    FOR EACH ROW EXECUTE FUNCTION reject_demo_evidence_mutation();
CREATE TRIGGER application_audit_immutable BEFORE UPDATE OR DELETE ON application_audit
    FOR EACH ROW EXECUTE FUNCTION reject_demo_evidence_mutation();
CREATE TRIGGER mutation_requests_immutable BEFORE UPDATE OR DELETE ON mutation_requests
    FOR EACH ROW EXECUTE FUNCTION reject_demo_evidence_mutation();
