package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Khalti integration. Same honest-limitation note as EsewaPaymentGateway applies
 * here — simulated response shape, real API call point marked in comments,
 * pending real merchant credentials.
 */
@Component
public class KhaltiPaymentGateway implements PaymentGateway {

    @Override
    public GatewayType getType() {
        return GatewayType.KHALTI;
    }

    @Override
    public PaymentInitiationResult initiate(Order order, UUID idempotencyKey) {
        // REAL IMPLEMENTATION: Khalti's "pidx" (payment index) initiation call —
        // similar shape to eSewa's, different field names/signing scheme per
        // Khalti's own API spec.
        String simulatedReference = "KHALTI-" + UUID.randomUUID();
        String simulatedRedirectUrl = "https://simulated.khalti.example/pay?pidx=" + simulatedReference;
        return new PaymentInitiationResult(simulatedRedirectUrl, simulatedReference);
    }

    @Override
    public PaymentVerificationResult verify(String gatewayReference) {
        // REAL IMPLEMENTATION: Khalti's lookup API, server-to-server, same
        // "never trust a client-side redirect param alone" reasoning as eSewa.
        // amountReceived null in simulation — see EsewaPaymentGateway's
        // identical comment for why, and what a real implementation must do.
        return new PaymentVerificationResult(true, gatewayReference, "SIMULATED_SUCCESS", null);
    }
}