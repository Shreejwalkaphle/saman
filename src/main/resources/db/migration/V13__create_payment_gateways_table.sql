-- THE DB-backed enable/disable mechanism roadmap doc Section 9 specifically calls
-- for: "gated by a DB-backed is_enabled config flag per gateway (not a hardcoded
-- env toggle) so it can be switched on via admin panel." This table IS that
-- mechanism — PaymentGatewayFactory (built later) reads from here at runtime to
-- decide which implementations are actually selectable, rather than any
-- environment-variable/code-level toggle.
CREATE TABLE payment_gateways (
                                  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  name         VARCHAR(30) NOT NULL UNIQUE,   -- 'ESEWA', 'KHALTI', 'STRIPE'
                                  is_enabled   BOOLEAN NOT NULL DEFAULT false,
                                  display_name VARCHAR(50) NOT NULL,
                                  created_at   TIMESTAMP NOT NULL DEFAULT now(),
                                  updated_at   TIMESTAMP NOT NULL DEFAULT now()
);

-- Seed data matches roadmap doc exactly: eSewa + Khalti live at launch, Stripe
-- built but dormant (is_enabled = false) until Nepal's cross-border payment
-- framework formally opens (roadmap doc's own stated timeline/reasoning).
INSERT INTO payment_gateways (name, is_enabled, display_name) VALUES
                                                                  ('ESEWA', true, 'eSewa'),
                                                                  ('KHALTI', true, 'Khalti'),
                                                                  ('STRIPE', false, 'Credit/Debit Card (Stripe)');