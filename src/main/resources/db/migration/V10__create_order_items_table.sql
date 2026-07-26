CREATE TABLE order_items (
                             id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             order_id            UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,

    -- RESTRICT here too, for the same permanent-record reasoning as orders.user_id
    -- above — an order line item must always be traceable to a real product.
                             product_id          UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,

    -- Product name/price snapshotted AGAIN here (not just referencing product_id)
    -- — this is deliberate duplication, not an oversight. An order_item is a
    -- PERMANENT financial record: if the product is later renamed or re-priced,
    -- or even deactivated, this row must still show EXACTLY what was actually
    -- purchased and at what price, forever. This is the same price-snapshot
    -- principle as cart_items, but even more important here since orders are
    -- legal/financial records, not a mutable shopping-in-progress state.
                             product_name        VARCHAR(200) NOT NULL,
                             price_at_purchase   NUMERIC(10, 2) NOT NULL,
                             quantity            INT NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);