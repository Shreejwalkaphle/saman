package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShopResponse(UUID id, String name, String slug, String phone,
                           String addressLine1, String city, String district,
                           BigDecimal latitude, BigDecimal longitude,
                           String status, String rejectionReason) {}
