package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Map;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        String gateway,
        String status,
        BigDecimal amount,
        String currency,
        String redirectUrl,
        String redirectMethod,
        Map<String, String> redirectFields
) {
}
