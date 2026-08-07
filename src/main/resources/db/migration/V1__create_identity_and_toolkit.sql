CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    ccgid VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE toolkit (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    gbs_center VARCHAR(120) NOT NULL,
    domain VARCHAR(120) NOT NULL,
    process_level_1 VARCHAR(160) NOT NULL,
    process_level_2 VARCHAR(160) NOT NULL,
    process_level_3 VARCHAR(160) NOT NULL,
    customer_country VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE toolkit_subtask (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    name VARCHAR(160) NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT uk_toolkit_subtask UNIQUE (toolkit_id, name)
);

CREATE TABLE toolkit_agent_assignment (
    id UUID PRIMARY KEY,
    toolkit_id UUID NOT NULL REFERENCES toolkit(id),
    user_id UUID NOT NULL REFERENCES app_user(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_toolkit_agent_assignment UNIQUE (toolkit_id, user_id)
);

INSERT INTO app_user (id, ccgid, display_name, email)
VALUES ('00000000-0000-0000-0000-000000000001', 'AGENT001', 'Demo Agent', 'agent@example.com');

INSERT INTO toolkit (
    id, code, name, gbs_center, domain, process_level_1, process_level_2,
    process_level_3, customer_country)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'bank-rec-manual', 'Bank Rec Manual Check',
     'Kuala Lumpur', 'Finance', 'Accounting', 'Record to Report', 'Bank Reconciliation', 'Australia'),
    ('10000000-0000-0000-0000-000000000002', 'bank-rec-auto', 'Bank Rec Auto Check',
     'Kuala Lumpur', 'Finance', 'Accounting', 'Record to Report', 'Bank Reconciliation', 'Australia'),
    ('10000000-0000-0000-0000-000000000003', 'accounts-payable', 'Accounts Payable',
     'Chennai', 'Finance', 'Accounting', 'Procure to Pay', 'Invoice Processing', 'Australia');

INSERT INTO toolkit_subtask (id, toolkit_id, name, display_order)
VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Manual match', 1),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'RFI follow-up', 2),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'Posting check', 3),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000002', 'Exception review', 1),
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002', 'Auto-match validation', 2),
    ('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002', 'Posting check', 3),
    ('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000003', 'Invoice validation', 1),
    ('20000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000003', 'Exception handling', 2),
    ('20000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000003', 'Payment preparation', 3);

INSERT INTO toolkit_agent_assignment (id, toolkit_id, user_id)
SELECT gen_random_uuid(), id, '00000000-0000-0000-0000-000000000001'
FROM toolkit;
