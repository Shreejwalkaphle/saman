package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Mirrors PaymentGatewayConfig exactly (same naming-collision-avoidance
 * reasoning — this is data ABOUT a partner, DeliveryPartner interface below
 * IS a partner's implementation contract).
 */
@Entity
@Table(name = "delivery_partners")
public class DeliveryPartnerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private DeliveryPartnerType name;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    protected DeliveryPartnerConfig() {
    }

    public UUID getId() { return id; }
    public DeliveryPartnerType getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public String getDisplayName() { return displayName; }
}