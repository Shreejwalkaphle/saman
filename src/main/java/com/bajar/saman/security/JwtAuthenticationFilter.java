package com.bajar.saman.security;

import com.bajar.saman.entity.Role;
import com.bajar.saman.entity.User;
import com.bajar.saman.entity.UserRole;
import com.bajar.saman.repository.UserRepository;
import com.bajar.saman.repository.UserRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs once per incoming HTTP request (that's what "OncePerRequestFilter" guarantees —
 * without it, some servlet containers can invoke a filter more than once per request
 * in certain forwarding scenarios).
 *
 * Job of this filter: look for a JWT on the request, and if it's present and valid,
 * tell Spring Security "this request belongs to this authenticated user, with these
 * roles." It does NOT decide which endpoints require auth — that's SecurityConfig's job.
 * This filter only answers "who is making this request, if anyone."
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            UserRoleRepository userRoleRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // ---- STEP 1: Pull the "Authorization" header off the incoming request ----
        // Standard convention: "Authorization: Bearer <token>"
        String authHeader = request.getHeader("Authorization");

        // If there's no header, or it doesn't start with "Bearer ", this request has no
        // JWT attached at all. We do NOT reject it here — some endpoints are public
        // (e.g. /api/auth/login, /api/auth/register). We just move on to the next
        // filter with no authentication set. If the endpoint actually requires auth,
        // Spring Security's authorization rules (in SecurityConfig) will reject it
        // further down the chain anyway.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip the "Bearer " prefix (7 characters) to get the raw token string.
        String token = authHeader.substring(7);

        // ---- STEP 2: Validate the token's signature and expiry ----
        if (!jwtService.isTokenValid(token)) {
            // Token is expired, tampered with, or malformed. Same reasoning as above —
            // don't throw an error here, just skip setting authentication. Downstream
            // authorization rules reject the request with 401/403 if auth was required.
            filterChain.doFilter(request, response);
            return;
        }

        // ---- STEP 3: Extract the user's identity (UUID) from the token's "sub" claim ----
        UUID userId = jwtService.extractUserId(token);

        // ---- STEP 4: Load this user's CURRENT state fresh from the database ----
        // Deliberate design choice explained when JwtService was built: roles are never
        // embedded in the token itself, so we look them up here, live, every request.
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            // Token is validly signed, but the user it points to no longer exists
            // (e.g. account deleted after this token was issued). Treat as
            // unauthenticated rather than erroring out.
            filterChain.doFilter(request, response);
            return;
        }

        User user = userOptional.get();

        // Extra safety net: even if the user still exists, if their account has since
        // been deactivated (is_active = false), they should not be treated as logged
        // in — closes the same "stale token" gap for account suspension, not just
        // role changes.
        if (!user.isActive()) {
            filterChain.doFilter(request, response);
            return;
        }

        // ---- STEP 5: Convert this user's DB roles into Spring Security "authorities" ----
        // Spring Security's convention: role-based authorities MUST be prefixed with
        // "ROLE_" (e.g. our CUSTOMER role becomes authority "ROLE_CUSTOMER"). This
        // specific prefix is what lets us later write hasRole("CUSTOMER") in
        // @PreAuthorize annotations — Spring strips "ROLE_" automatically when you use
        // hasRole(), so we must add it ourselves here for that shorthand to work later.
        List<UserRole> userRoles = userRoleRepository.findByUser_Id(userId);

        List<GrantedAuthority> authorities = userRoles.stream()
                .map(UserRole::getRole)               // UserRole -> Role
                .map(Role::getName)                    // Role -> "CUSTOMER" / "ADMIN" / etc.
                .map(roleName -> new SimpleGrantedAuthority("ROLE_" + roleName))
                .collect(Collectors.toList());

        // ---- STEP 6: Tell Spring Security "this request is authenticated" ----
        // UsernamePasswordAuthenticationToken is Spring Security's generic container for
        // "an authenticated principal + their authorities" — despite the name, it's not
        // specific to username/password login, it's just the standard authenticated-
        // token class used throughout the framework.
        //   - principal: the full User entity (so controllers can pull the authenticated
        //     User straight out of the SecurityContext later, without a re-query)
        //   - credentials: null (we don't need to carry a password around post-auth)
        //   - authorities: the ROLE_* list built above
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(user, null, authorities);

        // Attach this authenticated token to the CURRENT request's SecurityContext.
        // From here on, for the rest of THIS single request's lifecycle, Spring
        // Security treats this request as "logged in" as this user.
        SecurityContextHolder.getContext().setAuthentication(authToken);

        // ---- STEP 7: Hand off to the next filter/controller in the chain ----
        // This filter's only job is to SET UP authentication — it doesn't decide which
        // endpoints need it or which roles are allowed there. That's SecurityConfig's
        // authorizeHttpRequests rules (next file).
        filterChain.doFilter(request, response);
    }
}