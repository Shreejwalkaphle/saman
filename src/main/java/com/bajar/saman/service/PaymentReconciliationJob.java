package com.bajar.saman.service;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Payment;
import com.bajar.saman.entity.PaymentStatus;
import com.bajar.saman.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final Duration minimumAge;
    private final Clock clock;

    @Autowired
    public PaymentReconciliationJob(
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            @Value("${app.payment.reconciliation-minimum-age:PT5M}") Duration minimumAge) {
        this(paymentRepository, paymentService, minimumAge, Clock.systemDefaultZone());
    }

    PaymentReconciliationJob(
            PaymentRepository paymentRepository,
            PaymentService paymentService,
            Duration minimumAge,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.minimumAge = minimumAge;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.payment.reconciliation-initial-delay-ms:60000}",
            fixedDelayString = "${app.payment.reconciliation-delay-ms:60000}")
    public void reconcileEsewaPayments() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(minimumAge);
        List<UUID> paymentIds = paymentRepository
                .findTop50ByGatewayAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        GatewayType.ESEWA, PaymentStatus.INITIATED, cutoff)
                .stream()
                .map(Payment::getId)
                .toList();

        for (UUID paymentId : paymentIds) {
            try {
                paymentService.reconcileInitiatedPayment(paymentId);
            } catch (RuntimeException ex) {
                // A gateway/network failure must leave the payment unresolved and
                // must not prevent other payments in this batch from reconciling.
                log.warn("Payment reconciliation deferred for paymentId={}", paymentId, ex);
            }
        }
    }
}
