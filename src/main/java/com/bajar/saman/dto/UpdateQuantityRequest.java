package com.bajar.saman.dto;

import jakarta.validation.constraints.Min;

public record UpdateQuantityRequest(
        @Min(value = 1, message = "Quantity must be at least 1")
        int quantity
) {
}