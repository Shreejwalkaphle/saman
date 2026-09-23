package com.bajar.saman.config;

import com.bajar.saman.security.JwtAuthenticationFilter;
import com.bajar.saman.security.RateLimitFilter;
import com.bajar.saman.security.RestAccessDeniedHandler;
import com.bajar.saman.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central security configuration: password hashing, session policy, which endpoints
 * are public vs protected, and the ORDER our custom filters run in.
 *
 * Filter order in this chain (earliest to latest):
 *   1. RateLimitFilter        — reject over-limit IPs cheaply, before any DB/JWT work
 *   2. JwtAuthenticationFilter — identify who's making the request (if anyone)
 *   3. Spring Security's built-in UsernamePasswordAuthenticationFilter (unused by us,
 *      but still part of the default chain)
 *   4. authorizeHttpRequests rules — decide if the (now-identified) request is
 *      allowed to reach this particular endpoint
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
// Turns on @PreAuthorize/@PostAuthorize support on service/controller methods.
// Off by default — without this, @PreAuthorize annotations would silently do
// nothing (no error, the check simply never runs), which would be a much
// more dangerous failure mode than a compile error.
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // Wires the CorsConfigurationSource bean (defined in CorsConfig)
                // into Spring Security's own filter chain — without this line,
                // Spring Security processes requests BEFORE Spring MVC's own CORS
                // handling would ever run, and CORS preflight (OPTIONS) requests
                // get rejected by the authorization rules below before they ever
                // reach the point where "this is just a CORS preflight, allow it"
                // would normally be recognized.
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Only credential-entry endpoints are public. `/api/auth/me`
                        // must pass through the authenticated rule below; permitting
                        // the entire `/api/auth/**` tree allowed an anonymous request
                        // to reach AuthController with a null principal and return 500.
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/auth/login", "/api/auth/register").permitAll()
                        // These read endpoints expose account/shop management data.
                        // Declare them before the public catalog wildcards below so
                        // anonymous callers fail in the filter chain with 401.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/shops/mine", "/api/products/shop/*/manage").authenticated()
                        // Browsing the catalog (viewing categories/products) must work
                        // WITHOUT being logged in — a customer shouldn't need an account
                        // just to look around. Only GET requests to these paths are
                        // public; POST/PUT/DELETE on the same paths still fall through
                        // to anyRequest().authenticated() below.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/categories/**", "/api/products/**", "/api/shops/**").permitAll()
                        // Uploaded product images must be viewable without login —
                        // same public-browsing reasoning as GET on /api/products above.
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/uploads/products/**").permitAll()
                        .anyRequest().authenticated()
                )
                // Tell Spring Security to route BOTH failure scenarios through our own
                // JSON-based handlers instead of its default (HTML error page /
                // near-empty response) behavior.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // STEP 1 first: register JwtAuthenticationFilter's position RELATIVE TO a
// well-known Spring Security filter class (UsernamePasswordAuthenticationFilter)
// — Spring already knows that filter's exact position internally, so this call
// succeeds and, as a side effect, now ALSO registers JwtAuthenticationFilter's
// own position in the chain.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
// STEP 2 second: NOW JwtAuthenticationFilter.class has a known position, so we
// can safely say "put rateLimitFilter right before it."
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
