package com.bajar.saman.service;

import com.bajar.saman.dto.AuthResponse;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.InvalidCredentialsException;
import com.bajar.saman.repository.UserRepository;
import com.bajar.saman.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService's login() logic — every dependency
 * (UserRepository, PasswordEncoder, JwtService, LoginAttemptService) is mocked, so
 * these tests run entirely in memory, with NO real database, NO real Spring context,
 * and NO real password hashing. This makes them extremely fast (milliseconds, not
 * seconds) and lets us test exact business-logic edge cases (locked account, wrong
 * password, email casing) without needing to manually reproduce that exact DB state
 * every time — which is exactly what we were doing by hand in Postman before this.
 */
@ExtendWith(MockitoExtension.class) // Activates Mockito's annotation processing
        // (@Mock, @InjectMocks below) for this test class.
class AuthenticationServiceTest {

    // @Mock creates a fake stand-in object for each dependency. By default, every
    // method on a mock does nothing / returns null / returns false — it only behaves
    // the way we explicitly tell it to (via when(...).thenReturn(...) below).
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private LoginAttemptService loginAttemptService;

    // @InjectMocks creates a REAL AuthenticationService instance, but automatically
    // wires in the 4 @Mock objects above through its constructor — this is the
    // actual class under test, everything it depends on is fake.
    @InjectMocks
    private AuthenticationService authenticationService;

    // Shared test data, rebuilt fresh before EVERY test method (see @BeforeEach)
    // so tests can never accidentally leak state into one another.
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("shreejwaltest@example.com", "hashed-password-value");
        // Reflection-free way to give this in-memory User a UUID for these tests —
        // normally Hibernate assigns this on save(), but we're not touching a real
        // DB here, so we set it directly via the constructor + package access isn't
        // available; instead we rely on JwtService being mocked (below) so the
        // actual ID value inside these tests barely matters — using a fresh random
        // UUID whenever it's needed within an individual test is more realistic than
        // trying to force one onto testUser here.
    }

    @Test
    void login_withCorrectPassword_returnsAuthResponseWithToken() {
        // ARRANGE: teach each mock what to do when called with these specific inputs
        String rawPassword = "password123";
        UUID userId = UUID.randomUUID();

        when(userRepository.findByEmail("shreejwaltest@example.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(rawPassword, testUser.getPasswordHash()))
                .thenReturn(true);
        when(jwtService.generateToken(any(), any()))
                .thenReturn("fake-jwt-token");

        // ACT
        AuthResponse response = authenticationService.login(
                "shreejwaltest@example.com", rawPassword);

        // ASSERT
        assertThat(response.token()).isEqualTo("fake-jwt-token");
        assertThat(response.email()).isEqualTo("shreejwaltest@example.com");

        // verify() confirms a specific interaction actually happened — here, that a
        // SUCCESSFUL login correctly notified LoginAttemptService to reset the
        // failed-attempt counter, and that recordFailedAttempt was NEVER called
        // (since the password was correct). This is exactly the kind of thing that
        // was silently broken in the earlier transaction-rollback bug — a test like
        // this, run at the time, would have failed immediately instead of only
        // being caught by manually checking pgAdmin.
        verify(loginAttemptService, times(1)).recordSuccessfulLogin(testUser);
        verify(loginAttemptService, never()).recordFailedAttempt(any());
    }

    @Test
    void login_withWrongPassword_throwsInvalidCredentialsAndRecordsFailedAttempt() {
        when(userRepository.findByEmail("shreejwaltest@example.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPasswordHash()))
                .thenReturn(false);

        // assertThatThrownBy: AssertJ's way of asserting "calling this code MUST
        // throw this specific exception type" — the lambda wraps the call so the
        // exception can be caught and inspected instead of crashing the test itself.
        assertThatThrownBy(() ->
                authenticationService.login("shreejwaltest@example.com", "wrongPassword")
        ).isInstanceOf(InvalidCredentialsException.class);

        // Confirms the failed attempt WAS recorded — this is the exact behavior
        // that was silently broken by the @Transactional rollback bug found
        // earlier. This test is the permanent guard against that bug quietly
        // coming back in a future refactor.
        verify(loginAttemptService, times(1)).recordFailedAttempt(testUser);
        verify(loginAttemptService, never()).recordSuccessfulLogin(any());
    }

    @Test
    void login_withNonExistentEmail_throwsInvalidCredentials_notADifferentException() {
        when(userRepository.findByEmail("doesnotexist@example.com"))
                .thenReturn(Optional.empty());

        // This test directly enforces the email-enumeration protection documented
        // on InvalidCredentialsException: a nonexistent email must throw the exact
        // SAME exception type as a wrong password, not some other exception that
        // might leak "this email isn't registered" to an attacker via a different
        // HTTP status or message.
        assertThatThrownBy(() ->
                authenticationService.login("doesnotexist@example.com", "anyPassword")
        ).isInstanceOf(InvalidCredentialsException.class);

        // Since the user was never found, passwordEncoder should never even be
        // asked to check anything — confirms the method short-circuits correctly
        // rather than wastefully (or dangerously) doing extra work.
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void login_withLockedAccount_throwsInvalidCredentials_evenWithCorrectPassword() {
        // Simulate an account that LoginAttemptService locked 5 minutes ago, for
        // another 10 minutes (i.e. still within the lockout window).
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(10));

        when(userRepository.findByEmail("shreejwaltest@example.com"))
                .thenReturn(Optional.of(testUser));

        assertThatThrownBy(() ->
                authenticationService.login("shreejwaltest@example.com", "password123")
        ).isInstanceOf(InvalidCredentialsException.class);

        // Critical assertion: passwordEncoder must NEVER be consulted for a locked
        // account — even if the caller happens to type the CORRECT password, the
        // lockout check must reject them before password verification even runs.
        // This directly tests the exact security property the lockout feature
        // exists for.
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void login_withMixedCaseEmail_normalizesBeforeLookup() {
        // Deliberately call login() with a DIFFERENT casing than what's "stored" —
        // this test exists specifically because of the email-normalization bug
        // fixed earlier in this project. If EmailNormalizer.normalize() were ever
        // accidentally removed from AuthenticationService in a future refactor,
        // THIS test is what would catch it — the mock below is set up to only
        // respond to the LOWERCASE key, so if the service queries with the raw
        // mixed-case input instead, findByEmail returns empty and the test fails.
        when(userRepository.findByEmail("shreejwaltest@example.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("fake-jwt-token");

        AuthResponse response = authenticationService.login(
                "ShreejwalTest@Example.com", "password123");

        assertThat(response.email()).isEqualTo("shreejwaltest@example.com");
        verify(userRepository).findByEmail("shreejwaltest@example.com");
    }
}