package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.OrderNotFoundException;
import com.bajar.saman.repository.OrderRepository;
import com.bajar.saman.repository.PaymentRepository;
import com.bajar.saman.service.payment.PaymentGateway;
import com.bajar.saman.service.payment.PaymentGatewayFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Service
public class PaymentService {

    // Closes a MEDIUM gap tracked in PROGRESS.md (§6): roadmap doc Section 7
    // explicitly required "audit logging for sensitive actions... payment
    // events" — this was a missed EXISTING requirement, not a newly-found
    // gap. Financial state transitions must always leave a server-side
    // trail, independent of whatever the client sees.
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGatewayFactory gatewayFactory;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderRepository orderRepository,
            PaymentGatewayFactory gatewayFactory) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.gatewayFactory = gatewayFactory;
    }

    /**
     * Starts a payment for an existing order. Same idempotency-first pattern as
     * OrderService.checkout() — a retry with the same key returns the existing
     * Payment record unchanged, rather than initiating a second payment attempt
     * with the gateway (which could otherwise result in a customer being charged
     * twice for one order if their first request's response was lost to a
     * network issue).
     */
    // Small wrapper — redirectUrl is deliberately NOT persisted on the Payment
    // entity (it's a one-time-use gateway URL with no lasting meaning after the
    // customer uses it), but the caller (controller) still needs it for THIS
    // one response. A record return type carries both without polluting the
    // entity's own persisted shape.
    public record PaymentInitiationOutcome(Payment payment, String redirectUrl) {
    }

    @Transactional
    public PaymentInitiationOutcome initiatePayment(User user, UUID orderId, GatewayType gatewayType, UUID idempotencyKey) {

        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            // On a retry, there's no fresh redirectUrl to give (the original
            // gateway call already happened) — null is correct here, the
            // client already has/used the original one from the first response.
            return new PaymentInitiationOutcome(existingPayment.get(), null);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        // Ownership check — same security property as CartService.getOwnedCartItem(),
        // same reasoning: without this, any authenticated user could pay for (or
        // probe the existence of) an order that isn't theirs by guessing/enumerating
        // order IDs.
        if (!order.getUser().getId().equals(user.getId())) {
            throw new OrderNotFoundException(orderId.toString()); // same exception
            // as "doesn't exist" — prevents order-ID enumeration, identical
            // information-leak reasoning as InvalidCredentialsException and
            // CartItemNotFoundException elsewhere in this project.
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidProductDataException(
                    "Order is not in a payable state (current status: " + order.getStatus() + ")");
        }

        // Factory enforces the DB-backed is_enabled gate — StripePaymentGateway
        // would be rejected here automatically while its config flag is false,
        // with zero special-case code needed in THIS method.
        PaymentGateway gateway = gatewayFactory.getGateway(gatewayType);

        PaymentGateway.PaymentInitiationResult result = gateway.initiate(order, idempotencyKey);

        Payment payment = new Payment(
                order, gatewayType, order.getTotalAmount(), order.getCurrency(), idempotencyKey);
        payment.setGatewayReference(result.gatewayReference());

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated: paymentId={}, orderId={}, gateway={}, amount={} {}",
                saved.getId(), order.getId(), gatewayType, saved.getAmount(), saved.getCurrency());
        return new PaymentInitiationOutcome(saved, result.redirectUrl());
    }

    /**
     * Confirms a payment's outcome with the gateway and updates both the Payment
     * and its parent Order accordingly. In a real deployment this would typically
     * be invoked from a webhook endpoint (the gateway pushes the result to us)
     * rather than only a polling call — the webhook-endpoint piece itself is
     * flagged as a follow-up in PROGRESS.md, this method is the shared logic
     * either path would call into.
     */
    @Transactional
    public Payment confirmPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        // Closes gap #3 tracked in PROGRESS.md (§6, HIGH priority): this
        // method previously called gateway.verify() unconditionally, every
        // time — including on a REDELIVERED confirmation for a payment
        // already resolved. Real gateways commonly redeliver webhooks
        // (at-least-once delivery guarantees, or a client retrying an
        // ambiguous network failure) — without this check, each redelivery
        // would trigger an unnecessary (and, with a real gateway, costly)
        // verification call. SUCCESS and FAILED are both terminal states —
        // once reached, this payment's outcome is final and re-verifying
        // can only ever confirm the same thing again, so short-circuiting
        // is always correct, never a missed state transition.
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        PaymentGateway gateway = gatewayFactory.getGateway(payment.getGateway());
        PaymentGateway.PaymentVerificationResult result = gateway.verify(payment.getGatewayReference());

        // Closes gap #5 tracked in PROGRESS.md (§6, HIGH priority): never
        // trust a bare success boolean alone for a financial confirmation —
        // independently verify the amount the gateway actually reports
        // receiving matches what THIS payment was created for.
        //
        // amountReceived == null is treated as "verification not applicable"
        // (current simulated gateways don't populate it — see each
        // gateway's own verify() comment) rather than a failure, so the
        // simulated end-to-end flow keeps working exactly as before this
        // fix. The moment ANY real gateway integration is built, that
        // gateway's verify() MUST populate amountReceived with a real
        // value — at that point this check becomes a REAL enforcement
        // point, not a no-op, without requiring any further change here.
        boolean amountMatches = result.amountReceived() == null
                || result.amountReceived().compareTo(payment.getAmount()) == 0;

        if (result.success() && amountMatches) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.getOrder().setStatus(OrderStatus.PAID); // dirty-checking handles
            // BOTH updates — Payment
            // and Order are both
            // loaded within this same
            // transaction/persistence
            // context.
            log.info("Payment SUCCEEDED: paymentId={}, orderId={}, amount={} {}",
                    payment.getId(), payment.getOrder().getId(), payment.getAmount(), payment.getCurrency());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            // Order deliberately stays PENDING, not moved to a "failed" status —
            // a failed payment attempt should allow the customer to simply retry
            // payment against the SAME order, not require creating a new order.
            // An amount MISMATCH is treated identically to a gateway-reported
            // failure here — either way, this specific payment attempt did not
            // legitimately complete and must not mark the order PAID.

            // WARN, not INFO — a failed/mismatched payment is worth surfacing
            // more prominently than a routine success, same log-level
            // reasoning as AuthenticationService's WARN on failed login.
            log.warn("Payment FAILED or amount mismatch: paymentId={}, orderId={}, gatewaySuccess={}, amountMatches={}",
                    payment.getId(), payment.getOrder().getId(), result.success(), amountMatches);
        }

        return payment;
    }
}