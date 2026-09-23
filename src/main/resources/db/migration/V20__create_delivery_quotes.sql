CREATE TABLE delivery_zones (
    id UUID PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    center_latitude NUMERIC(9,6) NOT NULL CHECK (center_latitude BETWEEN -90 AND 90),
    center_longitude NUMERIC(9,6) NOT NULL CHECK (center_longitude BETWEEN -180 AND 180),
    service_radius_km NUMERIC(6,2) NOT NULL CHECK (service_radius_km > 0),
    max_delivery_distance_km NUMERIC(6,2) NOT NULL CHECK (max_delivery_distance_km > 0),
    base_distance_km NUMERIC(6,2) NOT NULL CHECK (base_distance_km >= 0),
    base_fee NUMERIC(10,2) NOT NULL CHECK (base_fee >= 0),
    additional_fee_per_km NUMERIC(10,2) NOT NULL CHECK (additional_fee_per_km >= 0),
    quote_validity_minutes INTEGER NOT NULL CHECK (quote_validity_minutes BETWEEN 1 AND 60),
    pricing_version INTEGER NOT NULL CHECK (pricing_version > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

-- Provisional pilot boundary: a 10 km operational circle around central
-- Biratnagar. This is configuration, not a claim that the circle is the exact
-- municipal polygon. Replace it with an authoritative polygon/PostGIS zone
-- before a public city-wide launch.
INSERT INTO delivery_zones (
    id, code, name, city, district, center_latitude, center_longitude,
    service_radius_km, max_delivery_distance_km, base_distance_km,
    base_fee, additional_fee_per_km, quote_validity_minutes, pricing_version
) VALUES (
    'c1a73947-c94e-4f06-a35f-e4cfe6b0bf85', 'BIRATNAGAR_PILOT',
    'Biratnagar Pilot Zone', 'Biratnagar', 'Morang', 26.452500, 87.271800,
    10.00, 10.00, 3.00, 30.00, 10.00, 10, 1
);

CREATE TABLE delivery_quotes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    shop_id UUID NOT NULL REFERENCES shops(id) ON DELETE RESTRICT,
    zone_id UUID NOT NULL REFERENCES delivery_zones(id) ON DELETE RESTRICT,
    customer_latitude NUMERIC(9,6) NOT NULL CHECK (customer_latitude BETWEEN -90 AND 90),
    customer_longitude NUMERIC(9,6) NOT NULL CHECK (customer_longitude BETWEEN -180 AND 180),
    distance_km NUMERIC(6,3) NOT NULL CHECK (distance_km >= 0),
    fee NUMERIC(10,2) NOT NULL CHECK (fee >= 0),
    currency VARCHAR(3) NOT NULL,
    pricing_version INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_delivery_quotes_user ON delivery_quotes(user_id);
CREATE INDEX idx_delivery_quotes_expires ON delivery_quotes(expires_at);

ALTER TABLE orders
    ADD COLUMN subtotal_amount NUMERIC(10,2),
    ADD COLUMN delivery_fee NUMERIC(10,2),
    ADD COLUMN delivery_quote_id UUID UNIQUE REFERENCES delivery_quotes(id) ON DELETE RESTRICT,
    ADD COLUMN shipping_latitude NUMERIC(9,6) CHECK (shipping_latitude BETWEEN -90 AND 90),
    ADD COLUMN shipping_longitude NUMERIC(9,6) CHECK (shipping_longitude BETWEEN -180 AND 180);

-- Existing local orders predate delivery quotes. Preserve their historical
-- totals while making the new fields truthful for those rows.
UPDATE orders SET subtotal_amount = total_amount, delivery_fee = 0
WHERE subtotal_amount IS NULL;

ALTER TABLE orders
    ALTER COLUMN subtotal_amount SET NOT NULL,
    ALTER COLUMN delivery_fee SET NOT NULL;
