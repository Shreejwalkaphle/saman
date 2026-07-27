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
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getCurrency() { return currency; }
    public java.math.BigDecimal getExchangeRateSnapshot() { return exchangeRateSnapshot; }
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
}