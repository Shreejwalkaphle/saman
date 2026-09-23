package com.bajar.saman.repository;

import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // THE idempotency check — OrderService will call this FIRST, before doing any
    // stock decrement or order creation. If a matching order already exists, it's
    // returned as-is instead of creating a duplicate — this is what actually
    // prevents a network-retry from double-charging/double-ordering.
    Optional<Order> findByIdempotencyKey(UUID idempotencyKey);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Order> findTop50ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            OrderStatus status, LocalDateTime createdBefore);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForPaymentOrExpiry(@Param("id") UUID id);
}
