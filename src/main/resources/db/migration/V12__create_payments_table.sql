CREATE TABLE payments (
                          id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- RESTRICT: same permanent-financial-record reasoning as orders.user_id — a
    -- payment must always remain traceable to the order it paid for.
                          order_id            UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,

                          gateway             VARCHAR(30) NOT NULL,      -- 'ESEWA', 'KHALTI', 'STRIPE'
                          status              VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
                          amount              NUMERIC(10, 2) NOT NULL,
                          currency            VARCHAR(3) NOT NULL,

    -- The gateway's OWN transaction/reference ID (eSewa's ref_id, Khalti's pidx,
    -- Stripe's payment_intent id) — needed to look up/verify a payment against the
    -- gateway's own API later, and to correlate an incoming webhook back to this
    -- row. Nullable because it's only known AFTER the gateway responds to the
    -- initiation call, not at row-creation time.
                          gateway_reference    VARCHAR(255),

    -- THE idempotency key for PAYMENT INITIATION specifically — same pattern,
    -- same reasoning as orders.idempotency_key (Cart/Checkout module), applied
    -- again here because payment initiation has the identical retry-safety
    -- requirement: a network failure during "start a payment" must not be able to
    -- create two separate payment attempts for the same order.
                          idempotency_key      UUID NOT NULL UNIQUE,

                          created_at          TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at          TIMESTAMP NOT NULL DEFAULT now(),
                          version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_gateway_reference ON payments(gateway_reference);

-- DELIBERATELY NOT ADDED YET (flagging in PROGRESS.md, not fixing now): no CHECK
-- constraint restricting `status`/`gateway` to fixed enum values — same deferral
-- reasoning as orders.status in the Checkout module (V9 migration comment):
-- waiting until the actual gateway integrations are built to know the real state
-- machine shape, rather than guessing it upfront.