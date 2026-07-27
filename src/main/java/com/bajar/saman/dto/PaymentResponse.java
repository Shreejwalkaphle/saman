package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String gateway,
        String status,
        BigDecimal amount,
        String currency,
        String redirectUrl // null once already-confirmed; populated on initiation
) {
}