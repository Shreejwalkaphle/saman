package com.bajar.saman.security;

import com.bajar.saman.dto.ErrorResponse;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Fires when a request IS authenticated (we know who they are) but they lack the
 * required role/permission for this specific endpoint — e.g. a CUSTOMER hitting an
 * ADMIN-only route. Not exercised by any real endpoint yet (nothing role-restricted
 * exists in the codebase so far), but wired in now so it's correct from day one of
 * the first @PreAuthorize/role-restricted endpoint, rather than being another
 * "discover it's broken via debugging" moment later.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    // Jackson 3.x: ObjectMapper is no longer directly instantiable/mutable —
    // JsonMapper (a JSON-specific subtype) with builder-based construction is now
    // the standard way to get a JSON mapper. Package also moved from
    // com.fasterxml.jackson to tools.jackson in this major version (Spring Boot 4.x
    // ships Jackson 3 by default — same category of "assume nothing, verify
    // current version" lesson as the Flyway and HttpServletResponse issues earlier).
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "You do not have permission to access this resource",
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}