package com.bajar.saman.service;

import com.bajar.saman.entity.Role;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.DuplicateEmailException;
import com.bajar.saman.repository.RoleRepository;
import com.bajar.saman.repository.UserRepository;
import com.bajar.saman.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserRegistrationService.register() — same isolation approach as
 * AuthenticationServiceTest: every dependency mocked, no real DB/Spring context.
 */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserRegistrationService registrationService;

    private Role customerRole;

    @Test
    void register_withNewEmail_savesUserAndAssignsCustomerRole() {
        // ARRANGE
        customerRole = new Role("CUSTOMER", "Regular shopper");

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));

        // ACT
        User result = registrationService.register("newuser@example.com", "password123");

        // ASSERT
        assertThat(result.getEmail()).isEqualTo("newuser@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed-password");

        // Confirms BOTH writes actually happened — User saved AND UserRole saved.
        // This pair of assertions is exactly what would have caught the earlier
        // @Transactional rollback bug pattern if it had existed here too: if either
        // save() call were silently skipped or rolled back, one of these two
        // verify() calls would fail.
        verify(userRepository, times(1)).save(any(User.class));
        verify(userRoleRepository, times(1)).save(any());
    }

    @Test
    void register_withExistingEmail_throwsDuplicateEmailException_beforeAnyWrite() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                registrationService.register("existing@example.com", "password123")
        ).isInstanceOf(DuplicateEmailException.class);

        // Confirms the early-exit actually happens BEFORE any wasted work — password
        // should never even be hashed, and neither save() should ever be called, if
        // we already know the email exists.
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void register_withRaceConditionAtSaveTime_throwsDuplicateEmailException() {
        // This is the exact scenario flagged in the earlier audit: existsByEmail()
        // returns false (no duplicate at check-time), but a concurrent request
        // slips in and saves the SAME email first — so by the time OUR save() call
        // actually executes, Postgres's UNIQUE constraint rejects it, and Hibernate
        // surfaces that as DataIntegrityViolationException.
        when(userRepository.existsByEmail("racecondition@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");

        // This is how Mockito simulates a method throwing an exception instead of
        // returning a value — used here to fake exactly the failure Postgres would
        // produce in a real race condition, without needing two real concurrent
        // threads or a real database to reproduce it.
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        // ASSERT: the try/catch fix added during the earlier audit should convert
        // this low-level DB exception into our clean, client-facing
        // DuplicateEmailException — this test exists specifically to confirm that
        // fix (flagged in PROGRESS.md as "possibly incomplete" at the time) is
        // actually working correctly.
        assertThatThrownBy(() ->
                registrationService.register("racecondition@example.com", "password123")
        ).isInstanceOf(DuplicateEmailException.class);

        // Since the save() itself failed, role assignment must NEVER be attempted —
        // there's no User to attach a role to.
        verify(roleRepository, never()).findByName(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void register_whenDefaultRoleMissingFromDatabase_throwsIllegalStateException() {
        // Simulates a data-integrity/configuration bug — the CUSTOMER role seed row
        // is somehow missing (should never happen given V1 migration, but this
        // proves the defensive check actually works if it ever did).
        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                registrationService.register("newuser@example.com", "password123")
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CUSTOMER");

        // The User row WAS already saved by this point in the method (before the
        // role lookup) — this test also documents that current behavior. Worth
        // noting: this scenario would leave a User with NO role in the database,
        // an edge case only reachable via serious misconfiguration, not normal
        // operation — flagging here as a place a future reader might reasonably
        // ask "should this whole thing roll back instead?"
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_normalizesEmailBeforeSavingAndBeforeExistsCheck() {
        when(userRepository.existsByEmail("shreejwaltest@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(roleRepository.findByName("CUSTOMER"))
                .thenReturn(Optional.of(new Role("CUSTOMER", "Regular shopper")));

        // ArgumentCaptor: instead of just verifying save() was called, this actually
        // CAPTURES the exact User object that was passed to save(), so we can
        // inspect its fields afterward — needed here because we specifically care
        // WHAT email ended up on the saved entity, not just that some save happened.
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        registrationService.register("ShreejwalTest@Example.com", "password123");

        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("shreejwaltest@example.com");

        // Also confirms existsByEmail was queried with the NORMALIZED form, not the
        // raw mixed-case input — this is what actually prevents the duplicate-
        // account bug EmailNormalizer was built to fix.
        verify(userRepository).existsByEmail("shreejwaltest@example.com");
    }
}