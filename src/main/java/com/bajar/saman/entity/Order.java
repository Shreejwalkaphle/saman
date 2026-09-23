package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // @Enumerated(EnumType.STRING): stores the enum's NAME ("PENDING", "PAID", etc.)
    // as text in the DB column, not its ordinal position (0, 1, 2...). STRING is
    // deliberately chosen over the (undeclared) default ORDINAL — if a future
    // developer reorders the OrderStatus enum's declared values, ORDINAL storage
    // would silently corrupt every existing order's status (an order stored as
    // "1" would suddenly mean a different status after reordering), whereas STRING
    // storage is immune to that entirely since it stores the actual name, not a
    // position.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "subtotal_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_quote_id", unique = true)
    private DeliveryQuote deliveryQuote;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    // Added when the Payment module was built (V11 migration) — see that
    // migration's comment for why this was late (Order predates the Payment
    // module's requirements being fully worked out) and why the delay caused no
    // data problem (added before any real payment data existed to backfill).
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "NPR";

    @Column(name = "exchange_rate_snapshot", precision = 12, scale = 6)
    private java.math.BigDecimal exchangeRateSnapshot;

    @Column(name = "shipping_address_line1", length = 255)
    private String shippingAddressLine1;

    @Column(name = "shipping_address_line2", length = 255)
    private String shippingAddressLine2;

    @Column(name = "shipping_city", length = 100)
    private String shippingCity;

    @Column(name = "shipping_district", length = 100)
    private String shippingDistrict;

    @Column(name = "shipping_postal_code", length = 20)
    private String shippingPostalCode;

    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;

    @Column(name = "shipping_latitude", precision = 9, scale = 6)
    private BigDecimal shippingLatitude;

    @Column(name = "shipping_longitude", precision = 9, scale = 6)
    private BigDecimal shippingLongitude;

    @Column(name = "delivery_partner", length = 50)
    private String deliveryPartner;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private java.time.LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private java.time.LocalDateTime deliveredAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // Bidirectional, same reasoning as Cart.items — an order's line items are
    // always accessed as a full group (viewing an order = seeing everything in
    // it), never navigated one-at-a-time via a separate query the way
    // Category's tree is.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(User user, BigDecimal totalAmount, UUID idempotencyKey) {
        this.user = user;
        this.totalAmount = totalAmount;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getSubtotalAmount() { return subtotalAmount; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public DeliveryQuote getDeliveryQuote() { return deliveryQuote; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getCurrency() { return currency; }
    public java.math.BigDecimal getExchangeRateSnapshot() { return exchangeRateSnapshot; }
    public String getShippingAddressLine1() { return shippingAddressLine1; }
    public String getShippingAddressLine2() { return shippingAddressLine2; }
    public String getShippingCity() { return shippingCity; }
    public String getShippingDistrict() { return shippingDistrict; }
    public String getShippingPostalCode() { return shippingPostalCode; }
    public String getShippingPhone() { return shippingPhone; }
    public BigDecimal getShippingLatitude() { return shippingLatitude; }
    public BigDecimal getShippingLongitude() { return shippingLongitude; }
    public String getDeliveryPartner() { return deliveryPartner; }
    public String getTrackingNumber() { return trackingNumber; }
    public java.time.LocalDateTime getShippedAt() { return shippedAt; }
    public java.time.LocalDateTime getDeliveredAt() { return deliveredAt; }

    public void setShippingAddress(String line1, String line2, String city,
                                   String district, String postalCode, String phone,
                                   BigDecimal latitude, BigDecimal longitude) {
        this.shippingAddressLine1 = line1;
        this.shippingAddressLine2 = line2;
        this.shippingCity = city;
        this.shippingDistrict = district;
        this.shippingPostalCode = postalCode;
        this.shippingPhone = phone;
        this.shippingLatitude = latitude;
        this.shippingLongitude = longitude;
    }

    /**
     * Called ONCE, when an order is first dispatched from the warehouse —
     * sets partner/tracking/shippedAt together (same atomic-related-fields
     * reasoning as the original markShipped()) and advances status to
     * SHIPPED_FROM_WAREHOUSE. Subsequent pipeline stages use
     * advanceDeliveryStatus() below, which doesn't touch partner/tracking
     * again (those don't change after initial dispatch).
     */
    public void dispatchFromWarehouse(String deliveryPartner, String trackingNumber) {
        this.deliveryPartner = deliveryPartner;
        this.trackingNumber = trackingNumber;
        this.shippedAt = java.time.LocalDateTime.now();
        this.status = OrderStatus.SHIPPED_FROM_WAREHOUSE;
    }

    /**
     * Advances the order to any LATER pipeline stage (IN_TRANSIT,
     * ARRIVED_AT_LOCAL_HUB, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED).
     * Legal-transition validation itself lives in OrderService (business-
     * rule ordering, matching where similar checks live throughout this
     * project — e.g. the original shipOrder()/markDelivered() status
     * guards) — this method just performs the actual state change once the
     * service layer has confirmed it's a valid transition.
     */
    public void advanceDeliveryStatus(OrderStatus newStatus) {
        this.status = newStatus;
        if (newStatus == OrderStatus.DELIVERED) {
            this.deliveredAt = java.time.LocalDateTime.now();
        }
    }
    public Long getVersion() { return version; }
    public List<OrderItem> getItems() { return items; }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
    // Package-private (no access modifier) rather than public — deliberately
    // restricts who can call this. The total is computed once, inside
    // OrderService.checkout(), from the actual cart items being processed (which
    // isn't known until the constructor has already run) — it should never be
    // recalculated or overwritten from anywhere else in the codebase afterward.
    // Package-private access enforces "only code in this same package (i.e.
    // OrderService, which lives in com.bajar.saman.service — actually this needs
    // to be public since OrderService is in a DIFFERENT package; see note below).
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void applyDeliveryQuote(BigDecimal subtotalAmount, DeliveryQuote quote) {
        this.subtotalAmount = subtotalAmount;
        this.deliveryFee = quote.getFee();
        this.totalAmount = subtotalAmount.add(quote.getFee());
        this.deliveryQuote = quote;
    }
}
