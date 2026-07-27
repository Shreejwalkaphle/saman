package com.bajar.saman.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitiatePaymentRequest(
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @NotNull(message = "Gateway is required")
        com.bajar.saman.entity.GatewayType gateway
) {
}