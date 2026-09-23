-- A blank start is effective immediately. A blank end does not expire.
ALTER TABLE rst_delegation
    ALTER COLUMN valid_from DROP NOT NULL,
    ALTER COLUMN valid_until DROP NOT NULL;
