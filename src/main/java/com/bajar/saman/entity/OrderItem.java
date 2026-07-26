package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A permanent line-item record within an order. Deliberately snapshots
 * productName and priceAtPurchase (not just a productId reference) — see V10
 * migration's comment for the full reasoning: this row must remain historically
 * accurate forever, even if the referenced Product is later renamed, re-priced,
 * or deactivated.
 */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "price_at_purchase", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItem() {
    }

    public OrderItem(Order order, Product product, String productName,
                     BigDecimal priceAtPurchase, int quantity) {
        this.order = order;
        this.product = product;
        this.productName = productName;
        this.priceAtPurchase = priceAtPurchase;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public BigDecimal getPriceAtPurchase() { return priceAtPurchase; }
    public int getQuantity() { return quantity; }
}