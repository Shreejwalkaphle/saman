-- Roadmap Addendum v2 §2.3: DB-backed enable/disable for delivery partners,
-- mirroring payment_gateways exactly (V13 migration) — same DeliveryPartner
-- Strategy+Factory pattern as PaymentGateway, gated the same way.
CREATE TABLE delivery_partners (
                                   id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                   name         VARCHAR(30) NOT NULL UNIQUE,   -- 'PATHAO', 'NCM'
                                   is_enabled   BOOLEAN NOT NULL DEFAULT true,
                                   display_name VARCHAR(50) NOT NULL,
                                   created_at   TIMESTAMP NOT NULL DEFAULT now(),
                                   updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

INSERT INTO delivery_partners (name, is_enabled, display_name) VALUES
                                                                   ('PATHAO', true, 'Pathao'),
                                                                   ('NCM', true, 'Nepal Can Move');