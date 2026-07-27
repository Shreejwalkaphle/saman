package com.bajar.saman.service.payment;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * eSewa integration.
 *
 * HONEST LIMITATION, flagged deliberately: this is a STRUCTURAL implementation
 * only — the actual HTTP call to eSewa's real API is NOT made here, because doing
 * so requires real merchant credentials (a signed sandbox/merchant account) that
 * don't exist in this project yet. `initiate()`/`verify()` below simulate a
 * plausible response shape so the REST of the payment flow (PaymentService,
 * PaymentController, order-status transitions) can be built and tested against a
 * realistic contract. The exact point where a real
 * `RestClient`/`WebClient` call to eSewa's actual endpoint would go is marked
 * with a comment — swapping the simulation for a real call is a contained,
 * well-scoped follow-up task once credentials exist, not a redesign.
 */
@Component
public class EsewaPaymentGateway implements PaymentGateway {

    @Override
    public GatewayType getType() {
        return GatewayType.ESEWA;
    }

    @Override
    public PaymentInitiationResult initiate(Order order, UUID idempotencyKey) {
        // REAL IMPLEMENTATION WOULD GO HERE: an HTTP POST to eSewa's payment
        // initiation endpoint, signed per eSewa's merchant integration spec,
        // passing order.getTotalAmount(), a success/failure callback URL, and a
        // transaction reference. eSewa's real flow returns a form-POST redirect
        // URL the frontend submits the customer's browser to.
        String simulatedReference = "ESEWA-" + UUID.randomUUID();
        String simulatedRedirectUrl = "https://simulated.esewa.example/pay?ref=" + simulatedReference;
        return new PaymentInitiationResult(simulatedRedirectUrl, simulatedReference);
    }

    @Override
    public PaymentVerificationResult verify(String gatewayReference) {
        // REAL IMPLEMENTATION WOULD GO HERE: an HTTP GET/POST to eSewa's
        // transaction-status verification endpoint, passing gatewayReference,
        // confirming the transaction's real status directly with eSewa rather
        // than trusting the value on a customer-facing redirect alone (redirect
        // parameters can be tampered with client-side; a server-to-server
        // verification call is a mandatory security step for any real
        // implementation of this method — noted here so this isn't accidentally
        // skipped when the simulation is later replaced with a real call).
        return new PaymentVerificationResult(true, gatewayReference, "SIMULATED_SUCCESS");
    }
}