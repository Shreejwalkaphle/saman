package com.bajar.saman.repository;

import com.bajar.saman.entity.Payment;
import com.bajar.saman.entity.GatewayType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    Optional<Payment> findByGatewayAndGatewayReference(
            GatewayType gateway, String gatewayReference);

    // Closes gap #4 tracked in PROGRESS.md (§6, HIGH priority). Changed
    // from Optional<Payment> to List<Payment> — a single order CAN have
    // multiple Payment rows (e.g. a failed eSewa attempt followed by a
    // successful Khalti retry, both against the SAME order). Optional
    // could not correctly represent this case (undefined which row it
    // would arbitrarily return). Ordered by most recent first, since
    // callers almost always care about the LATEST attempt's outcome.
    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
