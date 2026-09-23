package com.bajar.saman.service;

import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.OrderStatus;
import com.bajar.saman.entity.User;
import com.bajar.saman.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationJobTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderExpirationService expirationService;

    @Test
    void jobUsesThirtyMinuteCutoffAndContinuesAfterOneFailure() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC);
        Order first = order();
        Order second = order();
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 23, 9, 30);
        when(orderRepository.findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                OrderStatus.PENDING, cutoff)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary failure"))
                .when(expirationService).expireIfAbandoned(first.getId());

        new OrderExpirationJob(orderRepository, expirationService,
                Duration.ofMinutes(30), clock).expireAbandonedOrders();

        verify(expirationService).expireIfAbandoned(first.getId());
        verify(expirationService).expireIfAbandoned(second.getId());
    }

    private Order order() {
        Order order = new Order(new User(UUID.randomUUID() + "@saman.test", "hash"),
                BigDecimal.TEN, UUID.randomUUID());
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }
}
