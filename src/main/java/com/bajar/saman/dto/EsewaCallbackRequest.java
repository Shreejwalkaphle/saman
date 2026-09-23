package com.bajar.saman.dto;

import jakarta.validation.constraints.NotBlank;

public record EsewaCallbackRequest(
        @NotBlank(message = "eSewa callback data is required") String data
) {
}
