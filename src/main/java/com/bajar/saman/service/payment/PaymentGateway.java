package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;

import java.util.UUID;

/**
 * The Strategy pattern interface roadmap doc Section 9 calls for by name. Every
 * real gateway (eSewa, Khalti, Stripe) implements this SAME contract —
 * PaymentService (built next) depends only on this interface, never on a
 * concrete gateway class (Dependency Inversion, same principle as
 * PasswordEncoder/ImageStorageService elsewhere in this project). Adding a new
 * gateway later means writing one new implementing class; PaymentService and
 * PaymentGatewayFactory need zero changes beyond registering it.
 */
public interface PaymentGateway {

    GatewayType getType();

    /**
     * Starts a payment with the gateway. Returns a redirect URL (or equivalent)
     * the frontend sends the customer to, plus the gateway's own reference for
     * this attempt (used later to verify/correlate a webhook).
     */
    PaymentInitiationResult initiate(Order order, UUID idempotencyKey);

    /**
     * Confirms a payment's actual outcome with the gateway — called either from a
     * webhook handler (gateway pushes the result to us) or a polling/callback
     * endpoint (customer's browser redirects back, we ask the gateway to confirm),
     * depending on which pattern a given gateway uses.
     */
    PaymentVerificationResult verify(String gatewayReference);

    record PaymentInitiationResult(String redirectUrl, String gatewayReference) {
    }

    record PaymentVerificationResult(boolean success, String gatewayReference, String rawStatus) {
    }
}