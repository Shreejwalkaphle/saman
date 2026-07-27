-- Roadmap doc Section 9 (Payment module) explicitly calls for these two fields to
-- exist on Order "from day one" — flagged now, a bit late (Order was built before
-- the Payment module's requirements were revisited), but added before any real
-- payment data depends on Order's shape, so no backfill/migration-of-data problem
-- results from the delay.
ALTER TABLE orders
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'NPR',
    ADD COLUMN exchange_rate_snapshot NUMERIC(12, 6);

-- exchange_rate_snapshot is NULLABLE: for an NPR order (the only live case today),
-- there is no foreign-exchange conversion involved, so this stays NULL. It only
-- gets populated once a foreign-currency gateway (Stripe) is actually enabled and
-- processes a non-NPR order — at that point this column captures "what was the
-- NPR-equivalent exchange rate at the moment this specific order was placed,"
-- another instance of this project's established snapshot-for-financial-integrity
-- pattern (see cart_items.price_at_addition / order_items.price_at_purchase).