package com.bajar.saman.security;

import com.bajar.saman.dto.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Fires whenever Spring Security rejects a request because it has NO valid
 * authentication at all (missing token, expired token, malformed token) trying to
 * reach a protected endpoint. This is the filter-chain-level equivalent of
 * GlobalExceptionHandler's InvalidCredentialsException handler — same ErrorResponse
 * shape, different trigger point (filter chain, not a controller-thrown exception).
 * Closes gap #5 (previously #5, now tracked as resolved in this update) in
 * PROGRESS.md: "inconsistent error response shape for auth/authorization failures."
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Jackson 3.x: ObjectMapper is no longer directly instantiable/mutable —
    // JsonMapper (a JSON-specific subtype) with builder-based construction is now
    // the standard way to get a JSON mapper. Package also moved from
    // com.fasterxml.jackson to tools.jackson in this major version (Spring Boot 4.x
    // ships Jackson 3 by default — same category of "assume nothing, verify
    // current version" lesson as the Flyway and HttpServletResponse issues earlier).
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication required — missing, invalid, or expired token",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}