package com.bajar.saman.entity;

/**
 * Fixed set of states an Order can be in. Using a real Java enum (not a plain
 * String, unlike Role.name) because this set is small, well-known, and benefits
 * from compiler-enforced correctness — a typo like "PAYED" instead of "PAID" is a
 * compile error here, whereas it would be a silent runtime bug with a raw String.
 * The V9 migration's `status VARCHAR(30)` column stores this enum's name() as
 * text — see @Enumerated(EnumType.STRING) on Order.status for how that mapping works.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}