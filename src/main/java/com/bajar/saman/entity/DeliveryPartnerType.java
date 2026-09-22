package com.bajar.saman.entity;

/**
 * Mirrors GatewayType's role for Payment exactly — a shared source of truth
 * for "what delivery partners this platform knows about," used by both
 * DeliveryPartnerConfig (enable/disable state) and the DeliveryPartner
 * Strategy implementations.
 */
public enum DeliveryPartnerType {
    PATHAO,
    NCM
}