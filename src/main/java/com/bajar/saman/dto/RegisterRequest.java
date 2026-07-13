package com.bajar.saman.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the client sends us to register a new account.
 *
 * This is a plain data-holder (a "record" — Java's built-in immutable DTO type,
 * generates constructor/getters/equals/hashCode/toString automatically, so we don't
 * hand-write boilerplate for something that's just "a bag of fields").
 *
 * The @NotBlank / @Email / @Size annotations don't do anything by themselves — they
 * only activate when the controller method parameter is marked @Valid. Spring then
 * runs these checks BEFORE our controller code even executes, and auto-rejects
 * invalid requests with a 400 Bad Request (handled by GlobalExceptionHandler below).
 */
public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password
) {
}