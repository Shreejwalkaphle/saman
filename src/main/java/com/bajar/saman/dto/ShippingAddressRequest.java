package com.bajar.saman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(

        @NotBlank(message = "Address line 1 is required")
        @Size(max = 255)
        String addressLine1,

        @Size(max = 255)
        String addressLine2,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "District is required")
        @Size(max = 100)
        String district,

        @Size(max = 20)
        String postalCode,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        String phone
) {
}