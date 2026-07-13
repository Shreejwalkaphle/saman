package com.bajar.saman.service;

import com.bajar.saman.dto.AuthResponse;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidCredentialsException;
import com.bajar.saman.repository.UserRepository;
import com.bajar.saman.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bajar.saman.util.EmailNormalizer;

/**
 * Handles the LOGIN flow — verifying credentials and issuing a JWT.
 * Kept separate from UserRegistrationService: registration and login are different
 * business operations (Single Responsibility Principle, roadmap doc Section 4) even
 * though they share the same entity/repository.
 */
@Service
public class AuthenticationService {

    // Lockout policy constants — kept as named constants (not inline magic numbers)
    // so the actual policy is readable/adjustable in one obvious place, and so a
    // future admin-configurable version of this (e.g. reading from application
    // properties or a DB-backed config table) has an obvious place to plug in.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;


    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;

    }

    /**
     * @Transactional here is READ-ONLY-ish in spirit (we do write one field: failed
     * login attempt tracking, below) but we don't mark it readOnly=true because of
     * that write. Kept as a regular @Transactional so the failed-attempt-counter
     * update and the rest of this method are consistent as one unit.
     */
    @Transactional
    public AuthResponse login(String email, String rawPassword) {

        // Same normalization as registration — a user who registered as
        // "Test@Example.com" must still be able to log in typing
        // "test@example.com" (or any case variant). Without this, normalization at
        // registration alone would actually make things WORSE, not better: it would
        // silently change what got stored, while login still searched for whatever
        // raw casing the user happened to type.
        String normalizedEmail = EmailNormalizer.normalize(email);

        // Look up the user by email. Note: we do NOT throw a different exception here
        // vs. a wrong-password case below — both paths converge on the same
        // InvalidCredentialsException, for the email-enumeration reason explained in
        // that exception's own comment.
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        // Account lockout check (this column existed since V2 migration, we're now
        // actually USING it). If an admin or our own brute-force protection locked
        // this account, reject the login even if the password is correct — we don't
        // want a locked account to still be usable just because someone eventually
        // got the password right.
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(java.time.LocalDateTime.now())) {
            throw new InvalidCredentialsException();
        }

        // passwordEncoder.matches() re-hashes the raw input with the SAME salt that's
        // embedded in the stored hash, and compares the results. We never decrypt the
        // stored hash (BCrypt is one-way by design) — this comparison is the only way
        // to check a password against a BCrypt hash.
        boolean passwordMatches = passwordEncoder.matches(rawPassword, user.getPasswordHash());

        if (!passwordMatches) {
            // Delegates to LoginAttemptService's own independent transaction — see
            // that class for why this can't just be inline code in this method.
            loginAttemptService.recordFailedAttempt(user);
            throw new InvalidCredentialsException();
        }

        // Reset the failed-attempt counter on a successful login. Also delegated,
        // for the Single Responsibility reason noted in LoginAttemptService.
        loginAttemptService.recordSuccessfulLogin(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return new AuthResponse(token, user.getId(), user.getEmail());
    }
}