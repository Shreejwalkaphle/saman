package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.repository.OrderRepository;
import com.bajar.saman.repository.PaymentRepository;
import com.bajar.saman.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ProductRepository productRepository;

    @Test
    void abandonedOrderIsCancelledAndStockIsRestoredExactlyOnce() {
        Product product = product(8);
        Order order = orderWith(product, 2);
        when(orderRepository.findByIdForPaymentOrExpiry(order.getId()))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(List.of());
        when(productRepository.findByIdForCheckout(product.getId()))
                .thenReturn(Optional.of(product));

        OrderExpirationService service = service();
        assertThat(service.expireIfAbandoned(order.getId())).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(10);

        assertThat(service.expireIfAbandoned(order.getId())).isFalse();
        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(productRepository, times(1)).findByIdForCheckout(product.getId());
    }

    @Test
    void unresolvedPaymentPreventsCancellationAndStockRestoration() {
        Product product = product(8);
        Order order = orderWith(product, 2);
        Payment payment = new Payment(order, GatewayType.ESEWA, BigDecimal.TEN,
                "NPR", UUID.randomUUID());
        when(orderRepository.findByIdForPaymentOrExpiry(order.getId()))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(List.of(payment));

        assertThat(service().expireIfAbandoned(order.getId())).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(product.getStockQuantity()).isEqualTo(8);
        verifyNoInteractions(productRepository);
    }

    @Test
    void allFailedPaymentsAllowCancellation() {
        Product product = product(8);
        Order order = orderWith(product, 2);
        Payment failed = new Payment(order, GatewayType.ESEWA, BigDecimal.TEN,
                "NPR", UUID.randomUUID());
        failed.setStatus(PaymentStatus.FAILED);
        when(orderRepository.findByIdForPaymentOrExpiry(order.getId()))
                .thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()))
                .thenReturn(List.of(failed));
        when(productRepository.findByIdForCheckout(product.getId()))
                .thenReturn(Optional.of(product));

        assertThat(service().expireIfAbandoned(order.getId())).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(10);
    }

    private OrderExpirationService service() {
        return new OrderExpirationService(orderRepository, paymentRepository, productRepository);
    }

    private Product product(int stock) {
        Product product = new Product(null, "Phone", "phone", BigDecimal.TEN,
                "SKU-" + UUID.randomUUID(), stock);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        return product;
    }

    private Order orderWith(Product product, int quantity) {
        Order order = new Order(new User("customer@saman.test", "hash"),
                BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        order.getItems().add(new OrderItem(order, product, product.getName(),
                product.getPrice(), quantity));
        return order;
    }
}
