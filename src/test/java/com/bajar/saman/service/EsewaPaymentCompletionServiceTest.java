package com.bajar.saman.service;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.Payment;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.PaymentNotFoundException;
import com.bajar.saman.repository.PaymentRepository;
import com.bajar.saman.service.payment.EsewaPaymentGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EsewaPaymentCompletionServiceTest {

    @Mock private EsewaPaymentGateway esewaGateway;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    @InjectMocks
    private EsewaPaymentCompletionService service;

    @Test
    void validOwnedCallbackDelegatesToServerSideConfirmation() {
        User owner = user();
        Payment payment = payment(owner, new BigDecimal("100.00"));
        var callback = new EsewaPaymentGateway.VerifiedCallback(
                "txn-1", "100.0", "COMPLETE", "REF-1");
        when(esewaGateway.verifyCallback("signed-data")).thenReturn(callback);
        when(paymentRepository.findByGatewayAndGatewayReference(GatewayType.ESEWA, "txn-1"))
                .thenReturn(Optional.of(payment));
        when(paymentService.confirmPayment(owner, payment.getId())).thenReturn(payment);

        assertThat(service.complete(owner, "signed-data")).isSameAs(payment);
        verify(paymentService).confirmPayment(owner, payment.getId());
    }

    @Test
    void amountMismatchNeverCallsStatusConfirmation() {
        User owner = user();
        Payment payment = payment(owner, new BigDecimal("100.00"));
        when(esewaGateway.verifyCallback("signed-data"))
                .thenReturn(new EsewaPaymentGateway.VerifiedCallback(
                        "txn-1", "999.0", "COMPLETE", "REF-1"));
        when(paymentRepository.findByGatewayAndGatewayReference(GatewayType.ESEWA, "txn-1"))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.complete(owner, "signed-data"))
                .isInstanceOf(InvalidProductDataException.class)
                .hasMessageContaining("amount");
        verifyNoInteractions(paymentService);
    }

    @Test
    void anotherUserCannotCompleteOwnersPayment() {
        User owner = user();
        User attacker = user();
        Payment payment = payment(owner, new BigDecimal("100.00"));
        when(esewaGateway.verifyCallback("signed-data"))
                .thenReturn(new EsewaPaymentGateway.VerifiedCallback(
                        "txn-1", "100.0", "COMPLETE", "REF-1"));
        when(paymentRepository.findByGatewayAndGatewayReference(GatewayType.ESEWA, "txn-1"))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.complete(attacker, "signed-data"))
                .isInstanceOf(PaymentNotFoundException.class);
        verifyNoInteractions(paymentService);
    }

    private User user() {
        User user = new User(UUID.randomUUID() + "@saman.test", "hash");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Payment payment(User owner, BigDecimal amount) {
        Order order = new Order(owner, amount, UUID.randomUUID());
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        Payment payment = new Payment(order, GatewayType.ESEWA, amount, "NPR", UUID.randomUUID());
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        payment.setGatewayReference("txn-1");
        return payment;
    }
}
