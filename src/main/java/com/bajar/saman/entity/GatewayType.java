package com.bajar.saman.entity;

/**
 * The set of payment gateways this platform knows how to integrate with. Shared
 * by BOTH the Payment entity (which gateway processed a specific payment) and
 * PaymentGatewayConfig (which gateways are currently enabled) — a single source
 * of truth for "what gateways exist," rather than a free-text String repeated
 * (and potentially misspelled differently) in two places.
 */
public enum GatewayType {
    ESEWA,
    KHALTI,
    STRIPE
}