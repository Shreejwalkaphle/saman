package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 30)
    private GatewayType gateway;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "gateway_reference", length = 255)
    private String gatewayReference;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Payment() {
    }

    public Payment(Order order, GatewayType gateway, BigDecimal amount, String currency, UUID idempotencyKey) {
        this.order = order;
        this.gateway = gateway;
        this.amount = amount;
        this.currency = currency;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public GatewayType getGateway() { return gateway; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getGatewayReference() { return gatewayReference; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public Long getVersion() { return version; }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setGatewayReference(String gatewayReference) {
        this.gatewayReference = gatewayReference;
    }
}