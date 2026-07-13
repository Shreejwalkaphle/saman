package com.bajar.saman.util;

/**
 * Centralizes email normalization so registration and login always agree on what
 * "the same email" means. Without this, "Test@Example.com" (typed at registration)
 * and "test@example.com" (typed at login) would be treated as two different users
 * by Postgres's UNIQUE constraint and by findByEmail() — a real correctness bug,
 * not just a cosmetic one.
 *
 * A plain static utility (not a @Component/@Service) deliberately — this has no
 * dependencies, no state, and no reason to go through Spring's DI container. Making
 * it a Spring bean would add indirection with zero benefit.
 */
public final class EmailNormalizer {

    // Private constructor: this class is never meant to be instantiated, only used
    // via its static method — standard Java convention for a pure utility class.
    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        if (email == null) {
            return null;
        }
        // trim(): strips accidental leading/trailing whitespace (e.g. from copy-paste
        // or a sloppy mobile keyboard) that would otherwise make "email " and "email"
        // register as different accounts.
        // toLowerCase(): the actual case-insensitivity fix — RFC 5321 technically
        // allows the LOCAL part of an email to be case-sensitive, but essentially no
        // real-world mail provider (Gmail, Outlook, etc.) actually treats it that
        // way, and treating it as case-INSENSITIVE is the practical, user-expected
        // standard essentially every consumer platform follows.
        return email.trim().toLowerCase();
    }
}