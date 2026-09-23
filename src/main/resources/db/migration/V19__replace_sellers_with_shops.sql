-- Pre-production domain reset: existing commerce rows are local dummy data and
-- cannot be mapped truthfully from a person-owned Product to a verified Shop.
-- Reset the complete transactional graph so no item-less orders or orphaned
-- payment history survives the model replacement. Gateway/partner reference
-- configuration, users, roles and categories remain intact.
TRUNCATE TABLE payments, orders, carts, products CASCADE;

DELETE FROM user_roles
WHERE role_id IN (SELECT id FROM roles WHERE name = 'SELLER');
DELETE FROM roles WHERE name = 'SELLER';

ALTER TABLE users DROP COLUMN seller_status;
ALTER TABLE products DROP COLUMN seller_id;

CREATE TABLE shops (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    application_key UUID NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    latitude NUMERIC(9,6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_APPROVAL','ACTIVE','REJECTED','SUSPENDED')),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE shop_members (
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','MANAGER','PICKER')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMP NOT NULL,
    PRIMARY KEY (shop_id, user_id)
);

CREATE INDEX idx_shop_members_user ON shop_members(user_id);
CREATE INDEX idx_shops_status ON shops(status);
CREATE INDEX idx_shops_location ON shops(latitude, longitude);

ALTER TABLE products
    ADD COLUMN shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE RESTRICT;
CREATE INDEX idx_products_shop_id ON products(shop_id);
