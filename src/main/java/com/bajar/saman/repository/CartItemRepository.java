package com.bajar.saman.repository;

import com.bajar.saman.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    // Used to check "does this product already exist in this cart" — if so, the
    // service layer should INCREASE quantity on the existing row rather than
    // insert a duplicate (the cart_items UNIQUE(cart_id, product_id) constraint
    // would reject a duplicate insert anyway, but checking here first lets us
    // give correct "increase quantity" behavior instead of just catching and
    // converting a constraint-violation exception).
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);
}