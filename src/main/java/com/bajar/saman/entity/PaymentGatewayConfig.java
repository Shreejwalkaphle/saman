package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Maps to payment_gateways table — represents "is this gateway currently turned
 * on," per roadmap doc's requirement for a DB-backed (not hardcoded env toggle)
 * enable/disable mechanism, switchable via an admin panel. PaymentGatewayFactory
 * (built next) reads these rows at runtime to decide which PaymentGateway
 * implementations are actually selectable.
 *
 * Named "Config," not "PaymentGateway," specifically to avoid colliding with the
 * PAYMENT-GATEWAY-AS-STRATEGY-PATTERN interface (also to be named PaymentGateway)
 * — this entity is data ABOUT a gateway; the interface IS a gateway
 * implementation's contract. Same name for both would be confusing in every
 * import statement and stack trace that touches this module.
 */
@Entity
@Table(name = "payment_gateways")
public class PaymentGatewayConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private GatewayType name;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    protected PaymentGatewayConfig() {
    }

    public UUID getId() { return id; }
    public GatewayType getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public String getDisplayName() { return displayName; }
}