package com.bajar.saman.entity;

/**
 * Tracks a user's seller-approval state, per Roadmap Addendum v2 §1.4.
 * NULL on the User entity itself (not this enum) represents "never applied
 * to sell" — this enum only covers the states that exist ONCE someone has
 * applied.
 */
public enum SellerStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}