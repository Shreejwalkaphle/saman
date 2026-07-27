package com.bajar.saman.entity;

/**
 * Fixed lifecycle states for a Payment record. Same reasoning as OrderStatus
 * (Cart/Checkout module) for using a real enum + EnumType.STRING rather than a
 * raw String or ORDINAL storage — compiler-enforced correctness, and immunity to
 * silent corruption if the enum's declared order ever changes.
 */
public enum PaymentStatus {
    INITIATED,
    SUCCESS,
    FAILED,
    REFUNDED
}