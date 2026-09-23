package com.bajar.saman.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateDeliveryQuoteRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String district
) {}
