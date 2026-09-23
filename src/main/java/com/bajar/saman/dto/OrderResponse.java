package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal deliveryFee,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<OrderItemResponse> items,
        String shippingCity,
        BigDecimal shippingLatitude,
        BigDecimal shippingLongitude,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt
) {
    // Nested record — same reasoning as ProductImageController's inline DTO:
    // small, single-use, tightly coupled to OrderResponse specifically, doesn't
    // warrant a separate top-level file.
    public record OrderItemResponse(
            UUID productId,
            String productName,
            BigDecimal priceAtPurchase,
            int quantity
    ) {
    }
}
