package com.bajar.saman.service;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.Payment;
import com.bajar.saman.entity.PaymentStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    @Test
    void jobUsesFiveMinuteCutoffAndContinuesAfterOneFailure() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
        Payment first = payment();
        Payment second = payment();
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 23, 9, 55);
        when(paymentRepository.findTop50ByGatewayAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                GatewayType.ESEWA, PaymentStatus.INITIATED, cutoff))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("gateway unavailable"))
                .when(paymentService).reconcileInitiatedPayment(first.getId());

        new PaymentReconciliationJob(
                paymentRepository, paymentService, Duration.ofMinutes(5), clock)
                .reconcileEsewaPayments();

        verify(paymentService).reconcileInitiatedPayment(first.getId());
        verify(paymentService).reconcileInitiatedPayment(second.getId());
    }

    private Payment payment() {
        User user = new User(UUID.randomUUID() + "@saman.test", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        Order order = new Order(user, BigDecimal.TEN, UUID.randomUUID());
        Payment payment = new Payment(order, GatewayType.ESEWA,
                BigDecimal.TEN, "NPR", UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }
}
