-- Address snapshot — same reasoning as order_items' price/name snapshotting
-- (see Cart & Checkout design doc §2.2 and V10 migration comment): an order's
-- shipping destination must remain historically accurate even if the
-- customer's profile address later changes. No user_addresses table exists
-- yet (deliberately deferred — a "saved addresses" feature is a legitimate
-- future enhancement, out of current scope) — address is captured directly
-- on the order at checkout time instead.
ALTER TABLE orders
    ADD COLUMN shipping_address_line1 VARCHAR(255),
    ADD COLUMN shipping_address_line2 VARCHAR(255),
    ADD COLUMN shipping_city         VARCHAR(100),
    ADD COLUMN shipping_district     VARCHAR(100),
    ADD COLUMN shipping_postal_code  VARCHAR(20),
    ADD COLUMN shipping_phone        VARCHAR(20),

    -- Delivery tracking — simple fields, not a Strategy-pattern abstraction
    -- (unlike Payment's gateway abstraction) — deliberate scope decision,
    -- see this module's design discussion: no real courier API integration
    -- is planned yet, so building that abstraction now would be premature
    -- (YAGNI). If a real courier integration is ever built, THAT is the
    -- point to introduce a DeliveryPartner interface, not before.
    ADD COLUMN delivery_partner      VARCHAR(50),
    ADD COLUMN tracking_number       VARCHAR(100),
    ADD COLUMN shipped_at            TIMESTAMP,
    ADD COLUMN delivered_at          TIMESTAMP;

-- Nullable throughout: shipping_address_* is only required once checkout
-- actually collects it (frontend change needed — tracked as a follow-up,
-- not built in this pass), and delivery_partner/tracking_number/shipped_at/
-- delivered_at are only populated once an admin actually ships the order,
-- which happens well after order creation.