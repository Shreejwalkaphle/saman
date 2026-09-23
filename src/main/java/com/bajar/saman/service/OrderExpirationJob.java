package com.bajar.saman.service;

import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.OrderStatus;
import com.bajar.saman.repository.OrderRepository;
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
public class OrderExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderRepository orderRepository;
    private final OrderExpirationService expirationService;
    private final Duration expirationAge;
    private final Clock clock;

    @Autowired
    public OrderExpirationJob(OrderRepository orderRepository,
                              OrderExpirationService expirationService,
                              @Value("${app.order.expiration-age:PT30M}") Duration expirationAge) {
        this(orderRepository, expirationService, expirationAge, Clock.systemDefaultZone());
    }

    OrderExpirationJob(OrderRepository orderRepository,
                       OrderExpirationService expirationService,
                       Duration expirationAge,
                       Clock clock) {
        this.orderRepository = orderRepository;
        this.expirationService = expirationService;
        this.expirationAge = expirationAge;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${app.order.expiration-initial-delay-ms:60000}",
            fixedDelayString = "${app.order.expiration-delay-ms:60000}")
    public void expireAbandonedOrders() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(expirationAge);
        List<UUID> orderIds = orderRepository
                .findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(OrderStatus.PENDING, cutoff)
                .stream()
                .map(Order::getId)
                .toList();

        for (UUID orderId : orderIds) {
            try {
                if (expirationService.expireIfAbandoned(orderId)) {
                    log.info("Expired abandoned order and restored stock: orderId={}", orderId);
                }
            } catch (RuntimeException ex) {
                log.warn("Order expiration deferred for orderId={}", orderId, ex);
            }
        }
    }
}
