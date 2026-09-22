package com.bajar.saman.controller;

import com.bajar.saman.dto.InitiatePaymentRequest;
import com.bajar.saman.dto.PaymentResponse;
import com.bajar.saman.entity.Payment;
import com.bajar.saman.entity.User;
import com.bajar.saman.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Same Idempotency-Key header convention as OrderController.checkout() — kept
    // consistent across the platform's two money-moving endpoints deliberately,
    // rather than each inventing its own convention.
    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiate(
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest request) {

        PaymentService.PaymentInitiationOutcome outcome = paymentService.initiatePayment(
                user, request.orderId(), request.gateway(), idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(outcome.payment(), outcome.redirectUrl()));
    }

    /**
     * Simulates the gateway confirming a payment. In a real deployment this
     * would be a webhook endpoint the GATEWAY calls, not something a client
     * calls directly — deliberately simplified to a directly-callable endpoint
     * for now, since building real webhook signature verification without a
     * real gateway account to test against would be building untestable code.
     * Flagged clearly as a known simplification, not a production-ready webhook.
     */
    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<PaymentResponse> confirm(
            @AuthenticationPrincipal User user,
            @PathVariable UUID paymentId) {
        Payment payment = paymentService.confirmPayment(user, paymentId);
        return ResponseEntity.ok(toResponse(payment, null));
    }

    private PaymentResponse toResponse(Payment payment, String redirectUrl) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getGateway().name(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                redirectUrl
        );
    }
}
