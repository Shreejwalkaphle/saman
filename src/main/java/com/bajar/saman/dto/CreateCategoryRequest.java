package com.bajar.saman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        // Nullable and NOT @NotBlank/@NotNull — a missing parentId is valid, it
        // means "this is a root-level category," not a validation error.
        UUID parentId
) {
}