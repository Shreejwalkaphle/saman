package com.bajar.saman.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration — deliberately kept as its own small @Configuration class
 * rather than folded into SecurityConfig, even though Spring Security is what
 * ultimately consumes this bean (via .cors(...) in SecurityConfig). Single
 * Responsibility: this class answers ONE question ("which origins/methods/
 * headers are allowed"), independent of authentication/authorization rules.
 *
 * Locked to specific origins, NEVER a wildcard ("*") — matches roadmap doc
 * Section 7's explicit requirement. A wildcard origin combined with
 * allowCredentials(true) (needed here, since Authorization headers are
 * credentialed requests) is actually REJECTED by browsers outright as an
 * insecure combination — so this isn't just a best-practice choice, "*" would
 * not even function correctly alongside Bearer-token auth.
 */
@Configuration
public class CorsConfig {

    // Externalized via application.properties rather than hardcoded — the
    // production origin will differ from localhost:4200, and this way that
    // change never requires touching Java code, only config.
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);

        // Explicit method allow-list, not a wildcard — matches this project's
        // established allow-list-over-deny-list security philosophy (same
        // reasoning as LocalImageStorageService's file-extension allow-list).
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // "*" is acceptable here (unlike origins) — this only controls which
        // REQUEST headers the browser is permitted to send, not a security-
        // sensitive response-exposure setting. Authorization, Content-Type,
        // Idempotency-Key, and anything else this API needs are covered.
        configuration.setAllowedHeaders(List.of("*"));

        // REQUIRED for the Authorization header (our JWT) to ever reach the
        // backend from a browser — without this, the browser strips
        // credentialed headers from cross-origin requests entirely.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}