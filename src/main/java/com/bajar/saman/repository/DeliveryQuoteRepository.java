package com.bajar.saman.repository;

import com.bajar.saman.entity.DeliveryQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryQuoteRepository extends JpaRepository<DeliveryQuote, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from DeliveryQuote q where q.id = :id")
    Optional<DeliveryQuote> findByIdForCheckout(@Param("id") UUID id);
}
