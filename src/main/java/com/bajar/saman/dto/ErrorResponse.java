package com.bajar.saman.dto;

import java.time.Instant;

/**
 * Standard shape for EVERY error this API returns, regardless of what went wrong.
 * A consistent error envelope means the frontend can write ONE piece of error-handling
 * code that works for all endpoints, instead of guessing the shape per-error-type.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}