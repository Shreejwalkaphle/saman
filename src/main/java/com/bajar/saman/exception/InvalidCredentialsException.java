package com.bajar.saman.exception;

/**
 * Thrown when login fails — either the email doesn't exist, OR the password is wrong.
 *
 * IMPORTANT security note: we deliberately use ONE exception (and later, one identical
 * error message) for BOTH "email not found" and "wrong password" cases. If we told the
 * client which one specifically failed, an attacker could use that difference to
 * discover which emails are registered on our platform (an "email enumeration"
 * vulnerability) — they'd try a bunch of emails and see which ones say "wrong
 * password" (email exists) vs "not found" (email doesn't exist). Keeping the message
 * identical for both cases closes that leak.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}