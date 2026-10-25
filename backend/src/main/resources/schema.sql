CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    role VARCHAR(32) NOT NULL,
    company_name VARCHAR(255),
    inn VARCHAR(12),
    avg_monthly_turnover_rub BIGINT DEFAULT 0,
    annual_turnover_rub BIGINT DEFAULT 0,
    current_balance_rub BIGINT DEFAULT 0,
    months_with_bank INT DEFAULT 0,
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
    review_reason_type VARCHAR(32),
    scoring_score INT,
    calculated_pdn_percent DOUBLE PRECISION,
    max_limit_rub BIGINT,
    approved_amount BIGINT,
    approved_rate DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS application_fns_snapshots (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    inn VARCHAR(12) NOT NULL,
    company_name VARCHAR(255),
    state_status VARCHAR(64),
    tax_blocks_exist BOOLEAN,
    business_age_months INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS application_bki_snapshots (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    inn VARCHAR(12) NOT NULL,
    bki_score INT,
    monthly_payment_rub BIGINT,
    current_overdue_days INT,
    cnt_overdue_over_90d INT,
    risk_bucket VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS mock_fns_registry (
    inn VARCHAR(12) PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,
    state_status VARCHAR(64) NOT NULL,
    tax_blocks_exist BOOLEAN NOT NULL DEFAULT FALSE,
    registration_date DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS mock_bki_registry (
    inn VARCHAR(12) PRIMARY KEY,
    bki_score INT NOT NULL,
    total_monthly_payment_rub BIGINT NOT NULL DEFAULT 0,
    current_overdue_days INT NOT NULL DEFAULT 0,
    cnt_overdue_over_90d INT NOT NULL DEFAULT 0
);
