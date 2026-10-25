INSERT INTO users (username, role, company_name, inn, avg_monthly_turnover_rub, annual_turnover_rub, current_balance_rub, months_with_bank) VALUES
('client_good', 'CLIENT', 'ООО "Ромашка"', '7701234567', 1500000, 18000000, 350000, 24),
('client_bad', 'CLIENT', 'ООО "Однодневка"', '7702999999', 100000, 1200000, 5000, 2),
('bank_employee', 'EMPLOYEE', 'АО "АЛЬФА-БАНК"', '7728168971', 0, 0, 0, 0)
ON CONFLICT (username) DO NOTHING;

INSERT INTO mock_fns_registry (inn, company_name, state_status, tax_blocks_exist, registration_date) VALUES
('7701234567', 'ООО "Ромашка"', 'ACTIVE', FALSE, '2021-02-10'),
('7702999999', 'ООО "Однодневка"', 'BANKRUPT', TRUE, '2025-08-01')
ON CONFLICT (inn) DO NOTHING;

INSERT INTO mock_bki_registry (inn, bki_score, total_monthly_payment_rub, current_overdue_days, cnt_overdue_over_90d) VALUES
('7701234567', 750, 50000, 0, 0),
('7702999999', 320, 300000, 45, 2)
ON CONFLICT (inn) DO NOTHING;
