package com.bajar.saman.service;

import com.bajar.saman.dto.AuthResponse;
import com.bajar.saman.entity.RefreshSession;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidRefreshTokenException;
import com.bajar.saman.repository.RefreshSessionRepository;
import com.bajar.saman.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshSessionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshSessionRepository repository;
    private final JwtService jwtService;
    private final Duration refreshTtl;

    public RefreshSessionService(
            RefreshSessionRepository repository,
            JwtService jwtService,
            @Value("${app.auth.refresh-token-ttl:PT720H}") Duration refreshTtl) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public AuthResponse issue(User user) {
        return issueInFamily(user, UUID.randomUUID(), LocalDateTime.now());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse rotate(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshSession current = repository.findForUpdateByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (current.getRevokedAt() != null) {
            repository.revokeActiveFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!current.getExpiresAt().isAfter(now) || !current.getUser().isActive()) {
            current.revoke(now);
            throw new InvalidRefreshTokenException();
        }

        TokenAndSession replacement = createSession(current.getUser(), current.getFamilyId(), now);
        repository.save(replacement.session());
        current.rotateTo(replacement.session().getId(), now);
        return response(current.getUser(), replacement.rawToken());
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findForUpdateByTokenHash(hash(rawToken))
                .ifPresent(session -> session.revoke(LocalDateTime.now()));
    }

    private AuthResponse issueInFamily(User user, UUID familyId, LocalDateTime now) {
        TokenAndSession created = createSession(user, familyId, now);
        repository.save(created.session());
        return response(user, created.rawToken());
    }

    private TokenAndSession createSession(User user, UUID familyId, LocalDateTime now) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSession session = new RefreshSession(
                user, hash(rawToken), familyId, now.plus(refreshTtl), now);
        return new TokenAndSession(rawToken, session);
    }

    private AuthResponse response(User user, String refreshToken) {
        return new AuthResponse(
                jwtService.generateToken(user.getId(), user.getEmail()),
                user.getId(), user.getEmail(), refreshToken,
                jwtService.getExpirationSeconds());
    }

    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidRefreshTokenException();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record TokenAndSession(String rawToken, RefreshSession session) {}
}
