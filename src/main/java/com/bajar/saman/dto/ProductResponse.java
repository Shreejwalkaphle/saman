package com.bajar.saman.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Notice `category` here is a nested CategoryResponse, not just a categoryId — this
 * lets the frontend render "Category: Electronics" directly from one API call,
 * instead of needing a second round-trip just to resolve the category name. Small
 * deliberate denormalization at the API boundary, not in the database itself.
 */
public record ProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        BigDecimal price,
        String sku,
        int stockQuantity,
        boolean active,
        UUID sellerId,
        CategoryResponse category
) {
}
