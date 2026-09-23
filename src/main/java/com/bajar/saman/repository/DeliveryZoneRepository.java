package com.bajar.saman.repository;

import com.bajar.saman.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, UUID> {
    List<DeliveryZone> findAllByActiveTrue();
}
