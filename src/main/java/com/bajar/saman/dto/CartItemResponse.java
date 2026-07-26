package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID id,
        UUID productId,
        String productName,
        int quantity,
        BigDecimal priceAtAddition,
        BigDecimal lineTotal
) {
}