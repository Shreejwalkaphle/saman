package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a user's shopping cart. One cart per user, enforced both at the DB
 * level (carts.user_id UNIQUE, per V7 migration) and structurally here (a
 * one-to-one User <-> Cart relationship).
 */
@Entity
@Table(name = "carts")
public class Cart extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Bidirectional, unlike Category's parent/children — cart items are accessed
    // as a group constantly (every cart view), unlike a category tree which is
    // usually navigated one level at a time via repository queries. "mappedBy"
    // means CartItem.cart (not this field) owns the actual foreign key column;
    // this side is purely for convenient navigation.
    //
    // cascade = ALL: saving/deleting a Cart automatically saves/deletes its items
    // too — appropriate here because a CartItem has NO independent meaning outside
    // its parent Cart (unlike, say, a Product existing independently of any
    // Category it happens to be filed under).
    //
    // orphanRemoval = true: if a CartItem is removed from this list in Java code
    // (e.g. cart.getItems().remove(someItem)), Hibernate deletes that row from the
    // DB on flush — without this, removing an item from the in-memory list would
    // NOT delete the corresponding database row, a common source of "ghost rows"
    // bugs in naive cart implementations.
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Cart(User user) {
        this.user = user;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public List<CartItem> getItems() { return items; }
}