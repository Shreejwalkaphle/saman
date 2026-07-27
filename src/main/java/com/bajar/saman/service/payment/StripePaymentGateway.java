package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stripe integration — built but DORMANT, per roadmap doc's explicit instruction:
 * same PaymentGateway interface as the live gateways, gated by
 * payment_gateways.is_enabled = false (seeded that way in V13 migration).
 * PaymentGatewayFactory will refuse to select this implementation while that flag
 * stays false, regardless of this class existing and being a fully registered
 * Spring bean. Same simulation-pending-real-credentials note applies.
 */
@Component
public class StripePaymentGateway implements PaymentGateway {

    @Override
    public GatewayType getType() {
        return GatewayType.STRIPE;
    }

    @Override
    public PaymentInitiationResult initiate(Order order, UUID idempotencyKey) {
        // REAL IMPLEMENTATION: Stripe PaymentIntent creation via their Java SDK —
        // notably a different integration SHAPE than eSewa/Khalti's redirect-URL
        // pattern (Stripe typically returns a client_secret for a frontend SDK to
        // handle in-page, not necessarily a redirect URL) — a real implementation
        // may need to widen PaymentInitiationResult's shape when this is built.
        String simulatedReference = "STRIPE-" + UUID.randomUUID();
        String simulatedRedirectUrl = "https://simulated.stripe.example/checkout?session=" + simulatedReference;
        return new PaymentInitiationResult(simulatedRedirectUrl, simulatedReference);
    }

    @Override
    public PaymentVerificationResult verify(String gatewayReference) {
        return new PaymentVerificationResult(true, gatewayReference, "SIMULATED_SUCCESS");
    }
}