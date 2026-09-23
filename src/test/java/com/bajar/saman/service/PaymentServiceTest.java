package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.IdempotencyConflictException;
import com.bajar.saman.exception.OrderNotFoundException;
import com.bajar.saman.exception.PaymentNotFoundException;
import com.bajar.saman.repository.OrderRepository;
import com.bajar.saman.repository.PaymentRepository;
import com.bajar.saman.service.payment.PaymentGateway;
import com.bajar.saman.service.payment.PaymentGatewayFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentGatewayFactory gatewayFactory;
    @Mock private PaymentGateway gateway;

    @InjectMocks
    private PaymentService paymentService;

    private User buildUser(UUID id) {
        User user = new User("test@example.com", "hashed");
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    private Order buildOrder(UUID id, User user, BigDecimal amount) {
        Order order = new Order(user, amount, UUID.randomUUID());
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    @Test
    void initiatePayment_withKnownIdempotencyKey_returnsExistingPaymentWithNullRedirectUrl() {
        UUID idempotencyKey = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, user, new BigDecimal("100.00"));
        Payment existingPayment = new Payment(order, GatewayType.ESEWA,
                new BigDecimal("100.00"), "NPR", idempotencyKey);

        when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingPayment));

        var result = paymentService.initiatePayment(user, orderId, GatewayType.ESEWA, idempotencyKey);

        assertThat(result.payment()).isEqualTo(existingPayment);
        // No fresh redirect URL on a retry — the customer already has/used the
        // original one from the first, successful attempt.
        assertThat(result.redirectUrl()).isNull();

        // Confirms the short-circuit — order lookup, gateway factory, and save
        // should never be touched on a known-key retry.
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(gatewayFactory);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiatePayment_withAnotherUsersIdempotencyKey_hidesExistingPayment() {
        UUID key = UUID.randomUUID();
        User owner = buildUser(UUID.randomUUID());
        User attacker = buildUser(UUID.randomUUID());
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, owner, new BigDecimal("100.00"));
        Payment existing = new Payment(order, GatewayType.ESEWA, new BigDecimal("100.00"), "NPR", key);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                attacker, orderId, GatewayType.ESEWA, key))
                .isInstanceOf(PaymentNotFoundException.class);
        verifyNoInteractions(orderRepository, gatewayFactory);
    }

    @Test
    void initiatePayment_withSameKeyButDifferentGateway_rejectsRequestMismatch() {
        UUID key = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, user, new BigDecimal("100.00"));
        Payment existing = new Payment(order, GatewayType.ESEWA, new BigDecimal("100.00"), "NPR", key);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> paymentService.initiatePayment(
                user, orderId, GatewayType.KHALTI, key))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(orderRepository, gatewayFactory);
    }

    @Test
    void initiatePayment_forOrderOwnedByDifferentUser_throwsOrderNotFoundException() {
        UUID orderId = UUID.randomUUID();
        User owner = buildUser(UUID.randomUUID());
        User attacker = buildUser(UUID.randomUUID());
        Order order = new Order(owner, new BigDecimal("100.00"), UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                paymentService.initiatePayment(attacker, orderId, GatewayType.ESEWA, UUID.randomUUID())
        ).isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void initiatePayment_forAlreadyPaidOrder_throwsInvalidProductDataException() {
        UUID orderId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order order = new Order(user, new BigDecimal("100.00"), UUID.randomUUID());
        order.setStatus(OrderStatus.PAID); // already paid

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                paymentService.initiatePayment(user, orderId, GatewayType.ESEWA, UUID.randomUUID())
        ).isInstanceOf(InvalidProductDataException.class);

        verifyNoInteractions(gatewayFactory);
    }

    @Test
    void initiatePayment_forPendingOrder_callsGatewayAndSavesPayment() {
        UUID orderId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order order = new Order(user, new BigDecimal("100.00"), UUID.randomUUID());

        when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(gatewayFactory.getGateway(GatewayType.ESEWA)).thenReturn(gateway);
        when(gateway.initiate(eq(order), any()))
                .thenReturn(new PaymentGateway.PaymentInitiationResult(
                        "https://example.com/pay", "GET", java.util.Map.of(), "REF-123"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = paymentService.initiatePayment(user, orderId, GatewayType.ESEWA, UUID.randomUUID());

        assertThat(result.redirectUrl()).isEqualTo("https://example.com/pay");
        assertThat(result.payment().getGatewayReference()).isEqualTo("REF-123");
    }

    @Test
    void confirmPayment_whenGatewayReportsSuccess_marksPaymentSuccessAndOrderPaid() {
        UUID paymentId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order order = new Order(user, new BigDecimal("100.00"), UUID.randomUUID());
        Payment payment = new Payment(order, GatewayType.ESEWA, new BigDecimal("100.00"), "NPR", UUID.randomUUID());
        payment.setGatewayReference("REF-123");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(gatewayFactory.getGateway(GatewayType.ESEWA)).thenReturn(gateway);
        when(gateway.verify("REF-123", new BigDecimal("100.00")))
                .thenReturn(new PaymentGateway.PaymentVerificationResult(
                        true, true, "REF-123", "SUCCESS", new BigDecimal("100.00")));

        Payment result = paymentService.confirmPayment(user, paymentId);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        // Confirms the ORDER's status was also correctly transitioned via dirty-
        // checking — this is the actual business outcome the whole method exists
        // to produce, not just the Payment row's own status.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void confirmPayment_whenGatewayReportsFailure_marksPaymentFailedButOrderStaysPending() {
        UUID paymentId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order order = new Order(user, new BigDecimal("100.00"), UUID.randomUUID());
        Payment payment = new Payment(order, GatewayType.ESEWA, new BigDecimal("100.00"), "NPR", UUID.randomUUID());
        payment.setGatewayReference("REF-456");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(gatewayFactory.getGateway(GatewayType.ESEWA)).thenReturn(gateway);
        when(gateway.verify("REF-456", new BigDecimal("100.00")))
                .thenReturn(new PaymentGateway.PaymentVerificationResult(
                        true, false, "REF-456", "FAILED", new BigDecimal("100.00")));

        Payment result = paymentService.confirmPayment(user, paymentId);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        // THE key design property this test guards: a failed payment must leave
        // the order re-triable (still PENDING), not push it into some
        // unrecoverable failed state that would force the customer to build a
        // brand new order just to try paying again.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void confirmPayment_whenGatewayIsStillPending_keepsAttemptInitiated() {
        UUID paymentId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order order = new Order(user, new BigDecimal("100.00"), UUID.randomUUID());
        Payment payment = new Payment(order, GatewayType.ESEWA,
                new BigDecimal("100.00"), "NPR", UUID.randomUUID());
        payment.setGatewayReference("PENDING-REF");

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(gatewayFactory.getGateway(GatewayType.ESEWA)).thenReturn(gateway);
        when(gateway.verify("PENDING-REF", new BigDecimal("100.00")))
                .thenReturn(new PaymentGateway.PaymentVerificationResult(
                        false, false, "PENDING-REF", "PENDING", new BigDecimal("100.00")));

        Payment result = paymentService.confirmPayment(user, paymentId);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void confirmPayment_forAnotherUsersPayment_hidesPaymentAndDoesNotCallGateway() {
        UUID paymentId = UUID.randomUUID();
        User owner = buildUser(UUID.randomUUID());
        User attacker = buildUser(UUID.randomUUID());
        Order order = buildOrder(UUID.randomUUID(), owner, new BigDecimal("100.00"));
        Payment payment = new Payment(order, GatewayType.ESEWA,
                new BigDecimal("100.00"), "NPR", UUID.randomUUID());
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.confirmPayment(attacker, paymentId))
                .isInstanceOf(PaymentNotFoundException.class);
        verifyNoInteractions(gatewayFactory);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
