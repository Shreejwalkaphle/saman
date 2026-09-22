package com.bajar.saman.entity;

/**
 * Fixed set of states an Order can be in. Using a real Java enum (not a plain
 * String, unlike Role.name) because this set is small, well-known, and benefits
 * from compiler-enforced correctness — a typo like "PAYED" instead of "PAID" is a
 * compile error here, whereas it would be a silent runtime bug with a raw String.
 * The V9 migration's `status VARCHAR(30)` column stores this enum's name() as
 * text — see @Enumerated(EnumType.STRING) on Order.status for how that mapping works.
 */
/**
 * Expanded per Roadmap Addendum v2 §2.2 — replaces the original binary
 * SHIPPED/DELIVERED with a real multi-stage delivery pipeline. See
 * OrderService's ALLOWED_TRANSITIONS map for which transitions between
 * these states are actually legal — this enum only defines the possible
 * values, not the valid sequence.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED_FROM_WAREHOUSE,
    IN_TRANSIT,
    ARRIVED_AT_LOCAL_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    DELIVERY_FAILED
}