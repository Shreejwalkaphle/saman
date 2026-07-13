package com.bajar.saman.dto;

import java.util.UUID;

/**
 * What we send BACK to the client after a successful register or login.
 * Notice what's absent: no passwordHash, no mfaSecret, no internal entity fields —
 * only what the frontend actually needs (the token to attach to future requests,
 * plus a couple of display-friendly identity fields).
 */
public record AuthResponse(
        String token,
        UUID userId,
        String email
) {
}