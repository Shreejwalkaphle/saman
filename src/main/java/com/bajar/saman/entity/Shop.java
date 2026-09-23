package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shops")
public class Shop extends Auditable {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, unique = true, length = 180) private String slug;
    @Column(name = "application_key", nullable = false, unique = true) private UUID applicationKey;
    @Column(nullable = false, length = 20) private String phone;
    @Column(name = "address_line1", nullable = false) private String addressLine1;
    @Column(nullable = false, length = 100) private String city;
    @Column(nullable = false, length = 100) private String district;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal latitude;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal longitude;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ShopStatus status = ShopStatus.PENDING_APPROVAL;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @Version @Column(nullable = false) private Long version;

    protected Shop() {}

    public Shop(String name, String slug, UUID applicationKey, String phone, String addressLine1,
                String city, String district, BigDecimal latitude, BigDecimal longitude) {
        this.name = name;
        this.slug = slug;
        this.applicationKey = applicationKey;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.district = district;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public UUID getApplicationKey() { return applicationKey; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public ShopStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public Long getVersion() { return version; }
    public void approve() { status = ShopStatus.ACTIVE; rejectionReason = null; }
    public void reject(String reason) { status = ShopStatus.REJECTED; rejectionReason = reason; }
    public void suspend(String reason) { status = ShopStatus.SUSPENDED; rejectionReason = reason; }
}
