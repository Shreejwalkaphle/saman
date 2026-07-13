package com.bajar.saman.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * What the client sends us to log in. Deliberately NOT reusing RegisterRequest even
 * though both currently just hold "email + password" — registration and login are
 * different operations with different validation needs (e.g. we don't want to
 * enforce an 8-character minimum on login — a user with an old, shorter password
 * from before we added that rule should still be able to log in; only NEW passwords
 * get the length rule). Keeping them separate now avoids an awkward shared-DTO
 * refactor later.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}