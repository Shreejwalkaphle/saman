package com.bajar.saman.repository;

import com.bajar.saman.entity.GatewayType;
import com.bajar.saman.entity.PaymentGatewayConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentGatewayConfigRepository extends JpaRepository<PaymentGatewayConfig, UUID> {

    Optional<PaymentGatewayConfig> findByName(GatewayType name);
}