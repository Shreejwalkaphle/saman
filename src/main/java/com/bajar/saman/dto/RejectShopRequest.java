package com.bajar.saman.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectShopRequest(@NotBlank @Size(max = 500) String reason) {}
