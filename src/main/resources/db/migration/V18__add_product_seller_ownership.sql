ALTER TABLE products
    ADD COLUMN seller_id UUID REFERENCES users(id) ON DELETE RESTRICT;

CREATE INDEX idx_products_seller_id ON products(seller_id);

-- Existing products were created by administrators before seller ownership
-- existed, so seller_id deliberately remains nullable for those legacy rows.
