package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryQuoteResponse(UUID id, UUID shopId, String zoneCode, String zoneName,
                                    BigDecimal distanceKm, BigDecimal fee, String currency,
                                    int pricingVersion, LocalDateTime expiresAt) {}
