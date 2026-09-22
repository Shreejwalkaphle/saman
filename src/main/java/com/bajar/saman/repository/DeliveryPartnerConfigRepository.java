package com.bajar.saman.repository;

import com.bajar.saman.entity.DeliveryPartnerConfig;
import com.bajar.saman.entity.DeliveryPartnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryPartnerConfigRepository extends JpaRepository<DeliveryPartnerConfig, UUID> {
    Optional<DeliveryPartnerConfig> findByName(DeliveryPartnerType name);
}