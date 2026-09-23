package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_quotes")
public class DeliveryQuote extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "shop_id", nullable = false) private Shop shop;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "zone_id", nullable = false) private DeliveryZone zone;
    @Column(name = "customer_latitude", nullable = false, precision = 9, scale = 6) private BigDecimal customerLatitude;
    @Column(name = "customer_longitude", nullable = false, precision = 9, scale = 6) private BigDecimal customerLongitude;
    @Column(name = "distance_km", nullable = false, precision = 6, scale = 3) private BigDecimal distanceKm;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal fee;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "pricing_version", nullable = false) private int pricingVersion;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
    @Version @Column(nullable = false) private Long version;

    protected DeliveryQuote() {}
    public DeliveryQuote(User user, Shop shop, DeliveryZone zone, BigDecimal customerLatitude,
                         BigDecimal customerLongitude, BigDecimal distanceKm, BigDecimal fee,
                         LocalDateTime expiresAt) {
        this.user = user; this.shop = shop; this.zone = zone;
        this.customerLatitude = customerLatitude; this.customerLongitude = customerLongitude;
        this.distanceKm = distanceKm; this.fee = fee; this.currency = "NPR";
        this.pricingVersion = zone.getPricingVersion(); this.expiresAt = expiresAt;
    }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Shop getShop() { return shop; }
    public DeliveryZone getZone() { return zone; }
    public BigDecimal getCustomerLatitude() { return customerLatitude; }
    public BigDecimal getCustomerLongitude() { return customerLongitude; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public BigDecimal getFee() { return fee; }
    public String getCurrency() { return currency; }
    public int getPricingVersion() { return pricingVersion; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void markUsed() { usedAt = LocalDateTime.now(); }
}
