-- Roadmap Addendum v2 §1.4: seller approval workflow (maker-checker adapted
-- for e-commerce). NULL for users who never applied to sell (the vast
-- majority — regular customers) — only populated once a user opts into
-- selling at registration.
ALTER TABLE users
    ADD COLUMN seller_status VARCHAR(20);

-- No CHECK constraint restricting to fixed values yet — same deliberate
-- deferral pattern used for orders.status and payments.status (V9/V12
-- migrations): waiting until the real approval workflow is built and
-- tested before locking the exact value set at the DB level.