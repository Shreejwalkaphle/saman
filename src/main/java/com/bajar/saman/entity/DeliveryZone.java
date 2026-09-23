package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "delivery_zones")
public class DeliveryZone extends Auditable {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 50) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 100) private String city;
    @Column(nullable = false, length = 100) private String district;
    @Column(name = "center_latitude", nullable = false, precision = 9, scale = 6) private BigDecimal centerLatitude;
    @Column(name = "center_longitude", nullable = false, precision = 9, scale = 6) private BigDecimal centerLongitude;
    @Column(name = "service_radius_km", nullable = false, precision = 6, scale = 2) private BigDecimal serviceRadiusKm;
    @Column(name = "max_delivery_distance_km", nullable = false, precision = 6, scale = 2) private BigDecimal maxDeliveryDistanceKm;
    @Column(name = "base_distance_km", nullable = false, precision = 6, scale = 2) private BigDecimal baseDistanceKm;
    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2) private BigDecimal baseFee;
    @Column(name = "additional_fee_per_km", nullable = false, precision = 10, scale = 2) private BigDecimal additionalFeePerKm;
    @Column(name = "quote_validity_minutes", nullable = false) private int quoteValidityMinutes;
    @Column(name = "pricing_version", nullable = false) private int pricingVersion;
    @Column(nullable = false) private boolean active;
    @Version @Column(nullable = false) private Long version;

    protected DeliveryZone() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public BigDecimal getCenterLatitude() { return centerLatitude; }
    public BigDecimal getCenterLongitude() { return centerLongitude; }
    public BigDecimal getServiceRadiusKm() { return serviceRadiusKm; }
    public BigDecimal getMaxDeliveryDistanceKm() { return maxDeliveryDistanceKm; }
    public BigDecimal getBaseDistanceKm() { return baseDistanceKm; }
    public BigDecimal getBaseFee() { return baseFee; }
    public BigDecimal getAdditionalFeePerKm() { return additionalFeePerKm; }
    public int getQuoteValidityMinutes() { return quoteValidityMinutes; }
    public int getPricingVersion() { return pricingVersion; }
    public boolean isActive() { return active; }
}
