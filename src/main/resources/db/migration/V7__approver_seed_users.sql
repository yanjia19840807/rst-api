-- Seed CDH / LTH users for DevWorkflowRouter approval steps 2 and 3.
INSERT INTO app_user (id, ccgid, display_name, email, active, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000004', 'CDH001', 'Demo CDH',
       'cdh@example.com', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE id = '00000000-0000-0000-0000-000000000004' OR ccgid = 'CDH001');

INSERT INTO app_user (id, ccgid, display_name, email, active, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000005', 'LTH001', 'Demo LTH',
       'lth@example.com', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM app_user
    WHERE id = '00000000-0000-0000-0000-000000000005' OR ccgid = 'LTH001');
