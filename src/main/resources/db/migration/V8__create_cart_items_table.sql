CREATE TABLE cart_items (
                            id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            cart_id             UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,

    -- RESTRICT, not CASCADE: a product should not be deletable while it still
    -- sits in someone's cart — same reasoning as categories/products elsewhere in
    -- this schema. Forces explicit handling (e.g. "deactivate" via is_active,
    -- rather than hard-delete) if a product needs to stop being sellable while
    -- carts reference it.
                            product_id          UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,

                            quantity            INT NOT NULL CHECK (quantity > 0),

    -- THE price snapshot decision, in schema form: captured at the moment the item
    -- was added to the cart, never recalculated from products.price afterward.
                            price_at_addition   NUMERIC(10, 2) NOT NULL,

                            added_at            TIMESTAMP NOT NULL DEFAULT now(),

    -- A given product can only appear ONCE per cart — adding the same product
    -- again should increase quantity on the existing row, not create a duplicate
    -- row. Enforced here at the DB level so this rule can never be violated even
    -- by a future bug in service-layer logic.
                            UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);