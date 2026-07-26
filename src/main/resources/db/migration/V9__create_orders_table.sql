CREATE TABLE orders (
                        id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- RESTRICT: an order must always be traceable to a real user account for
    -- audit/legal reasons (purchase history, tax records) — a user should never
    -- be hard-deletable while they have order history. (This is a stronger
    -- reason than most other RESTRICT choices in this schema — orders are
    -- effectively permanent financial records.)
                        user_id             UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

                        status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    -- Snapshot of the cart's total at order-creation time — same price-integrity
    -- reasoning as cart_items.price_at_addition, one level up.
                        total_amount        NUMERIC(10, 2) NOT NULL,

    -- THE idempotency key, identified as a roadmap gap during this session's
    -- audit. Client generates a fresh UUID before submitting checkout, and resends
    -- the SAME key if retrying after a network timeout/ambiguous failure. UNIQUE
    -- constraint means a second attempt with the same key can never create a
    -- second order — OrderService will check for an existing order with this key
    -- FIRST and return it unchanged, rather than ever double-charging/double-
    -- ordering. Nullable is NOT allowed — every order must go through this path,
    -- there's no legitimate "order with no idempotency key" scenario.
                        idempotency_key     UUID NOT NULL UNIQUE,

                        created_at          TIMESTAMP NOT NULL DEFAULT now(),
                        updated_at          TIMESTAMP NOT NULL DEFAULT now(),

    -- Optimistic locking — an order's status will be mutated multiple times over
    -- its lifecycle (PENDING -> PAID -> SHIPPED -> DELIVERED, or -> CANCELLED),
    -- often triggered by external events (payment webhooks) that could arrive
    -- out of order or concurrently with an admin action — exactly the kind of
    -- multi-writer scenario @Version exists to protect.
                        version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_user_id ON orders(user_id);

-- DELIBERATELY NOT ADDED YET (flagging in PROGRESS.md, not fixing now): no CHECK
-- constraint restricting `status` to a fixed enum of valid values (e.g. PENDING,
-- PAID, SHIPPED, DELIVERED, CANCELLED) — deferred until the actual order state
-- machine is designed in the OrderService, matching this project's established
-- get-it-working-then-harden pattern for constraints that need real usage to
-- inform their exact shape.