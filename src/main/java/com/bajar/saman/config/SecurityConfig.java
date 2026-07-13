package com.bajar.saman.config;

import com.bajar.saman.security.JwtAuthenticationFilter;
import com.bajar.saman.security.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated()
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