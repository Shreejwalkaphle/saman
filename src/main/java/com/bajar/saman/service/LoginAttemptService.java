package com.bajar.saman.service;

import com.bajar.saman.entity.User;
import com.bajar.saman.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles persisting failed-login-attempt tracking and lockout state, in its OWN
 * independent database transaction — deliberately separate from
 * AuthenticationService.login()'s transaction. See the comment on
 * recordFailedAttempt() below for exactly why this separation is required, not
 * just a style preference.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Propagation.REQUIRES_NEW is the critical piece here.
     *
     * By DEFAULT (Propagation.REQUIRED), a @Transactional method called from inside
     * another @Transactional method just JOINS the caller's existing transaction —
     * it does not get an independent one. That means: if the CALLER later throws an
     * unchecked exception (which AuthenticationService.login() does on purpose,
     * immediately after calling this method — InvalidCredentialsException), Spring's
     * default rollback rule ("roll back on any RuntimeException") rolls back
     * EVERYTHING done in that SHARED transaction — including this method's save()
     * call. The failed-attempt counter would silently never persist. This was
     * exactly the bug: code visibly ran (visible in a debugger, which shows
     * in-memory/pending state), but nothing was ever actually committed to Postgres.
     *
     * REQUIRES_NEW forces Spring to: suspend the caller's (login()'s) transaction,
     * open a brand-new independent transaction, commit THIS method's work the
     * moment it returns, and only THEN resume the caller's transaction. By the time
     * login() goes on to throw InvalidCredentialsException, this method's write is
     * already permanently committed — that later exception can no longer touch it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(User user) {
        int updatedAttempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(updatedAttempts);

        if (updatedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
        }

        userRepository.save(user);
    }

    /**
     * A successful login doesn't throw afterward, so the REQUIRES_NEW reasoning
     * above doesn't strictly apply to this method the same way. Kept in this same
     * dedicated class anyway, for Single Responsibility: "login-attempt bookkeeping"
     * is one cohesive concern, not something to scatter partly here and partly in
     * AuthenticationService.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessfulLogin(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }
    }
}