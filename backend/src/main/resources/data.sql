INSERT INTO users (username, role, company_name, inn) VALUES
('client_good', 'CLIENT', 'ООО "Ромашка"', '7701234567'),
('client_bad', 'CLIENT', 'ООО "Однодневка"', '7702999999'),
('bank_employee', 'EMPLOYEE', 'АО "АЛЬФА-БАНК"', '7728168971')
ON CONFLICT (username) DO NOTHING;