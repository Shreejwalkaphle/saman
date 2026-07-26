package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // This is the OWNING side of the bidirectional relationship (holds the actual
    // FK column) — Cart.items is just the convenience navigation side, declared
    // "mappedBy" this field.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    // Price snapshot at the moment this item was added — deliberately NEVER
    // recalculated from product.getPrice() afterward. See V8 migration's comment
    // for the full reasoning (price integrity / no surprise cart-price changes).
    @Column(name = "price_at_addition", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtAddition;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    protected CartItem() {
    }

    public CartItem(Cart cart, Product product, int quantity, BigDecimal priceAtAddition) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.priceAtAddition = priceAtAddition;
        this.addedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public Cart getCart() { return cart; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPriceAtAddition() { return priceAtAddition; }
    public LocalDateTime getAddedAt() { return addedAt; }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}