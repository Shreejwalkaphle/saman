package com.bajar.saman.service;

import com.bajar.saman.dto.AuthResponse;
import com.bajar.saman.entity.RefreshSession;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidRefreshTokenException;
import com.bajar.saman.repository.RefreshSessionRepository;
import com.bajar.saman.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {
    @Mock private RefreshSessionRepository repository;
    @Mock private JwtService jwtService;

    private RefreshSessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshSessionService(repository, jwtService, Duration.ofDays(30));
        user = new User("customer@saman.test", "hash");
    }

    @Test
    void issueReturnsOpaqueTokenButStoresOnlyItsHash() {
        stubAccessToken();
        AuthResponse response = service.issue(user);

        ArgumentCaptor<RefreshSession> saved = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(saved.capture());
        assertThat(response.refreshToken()).isNotBlank().doesNotContain(".");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(response.refreshToken()));
        assertThat(saved.getValue().getTokenHash()).doesNotContain(response.refreshToken());
    }

    @Test
    void rotateRevokesOldTokenAndCreatesReplacementInSameFamily() {
        stubAccessToken();
        String raw = "old-refresh-token";
        UUID family = UUID.randomUUID();
        RefreshSession current = new RefreshSession(
                user, sha256(raw), family, LocalDateTime.now().plusDays(1), LocalDateTime.now());
        when(repository.findForUpdateByTokenHash(sha256(raw))).thenReturn(Optional.of(current));

        AuthResponse response = service.rotate(raw);

        ArgumentCaptor<RefreshSession> saved = ArgumentCaptor.forClass(RefreshSession.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(family);
        assertThat(current.getRevokedAt()).isNotNull();
        assertThat(current.getReplacedById()).isEqualTo(saved.getValue().getId());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(response.refreshToken()));
    }

    @Test
    void reuseOfRotatedTokenRevokesEntireActiveFamily() {
        String raw = "already-used-token";
        UUID family = UUID.randomUUID();
        RefreshSession reused = new RefreshSession(
                user, sha256(raw), family, LocalDateTime.now().plusDays(1), LocalDateTime.now());
        reused.revoke(LocalDateTime.now());
        when(repository.findForUpdateByTokenHash(sha256(raw))).thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).revokeActiveFamily(eq(family), any(LocalDateTime.class));
    }

    @Test
    void expiredTokenIsRevokedAndRejected() {
        String raw = "expired-token";
        RefreshSession expired = new RefreshSession(
                user, sha256(raw), UUID.randomUUID(), LocalDateTime.now().minusMinutes(1), LocalDateTime.now());
        when(repository.findForUpdateByTokenHash(sha256(raw))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(raw))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(expired.getRevokedAt()).isNotNull();
        verify(repository, never()).save(any());
    }

    @Test
    void logoutIsIdempotentForUnknownToken() {
        when(repository.findForUpdateByTokenHash(sha256("unknown"))).thenReturn(Optional.empty());
        service.revoke("unknown");
        verify(repository, never()).save(any());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }

    private void stubAccessToken() {
        when(jwtService.generateToken(user.getId(), user.getEmail())).thenReturn("access-token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);
    }
}
