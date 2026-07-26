package com.bajar.saman.service;

import com.bajar.saman.entity.Cart;
import com.bajar.saman.entity.CartItem;
import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.CartItemNotFoundException;
import com.bajar.saman.exception.InsufficientStockException;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.ProductNotFoundException;
import com.bajar.saman.repository.CartItemRepository;
import com.bajar.saman.repository.CartRepository;
import com.bajar.saman.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    /**
     * "Get or create" pattern — every authenticated user implicitly has a cart the
     * first time they need one, without a separate explicit "create my cart" API
     * call. This is called at the START of every other cart operation below.
     */
    @Transactional
    public Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(new Cart(user)));
    }

    @Transactional
    public CartItem addItem(User user, UUID productId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidProductDataException("Quantity must be greater than zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId.toString()));

        if (!product.isActive()) {
            throw new InvalidProductDataException("This product is no longer available");
        }

        Cart cart = getOrCreateCart(user);

        // "Already in cart?" check — if the customer adds the same product twice,
        // this INCREASES quantity on the existing row rather than creating a
        // duplicate (which the DB's UNIQUE(cart_id, product_id) constraint would
        // reject anyway) or silently failing.
        return cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .map(existingItem -> {
                    int newQuantity = existingItem.getQuantity() + quantity;
                    validateStockAvailable(product, newQuantity);
                    existingItem.setQuantity(newQuantity);
                    return existingItem; // dirty-checking handles the UPDATE — same
                    // pattern as ProductService.updatePrice
                })
                .orElseGet(() -> {
                    validateStockAvailable(product, quantity);
                    CartItem newItem = new CartItem(cart, product, quantity, product.getPrice());
                    return cartItemRepository.save(newItem);
                });
    }

    @Transactional
    public CartItem updateQuantity(User user, UUID cartItemId, int newQuantity) {
        if (newQuantity <= 0) {
            throw new InvalidProductDataException("Quantity must be greater than zero");
        }

        CartItem item = getOwnedCartItem(user, cartItemId);
        validateStockAvailable(item.getProduct(), newQuantity);

        item.setQuantity(newQuantity);
        return item;
    }

    @Transactional
    public void removeItem(User user, UUID cartItemId) {
        CartItem item = getOwnedCartItem(user, cartItemId);
        cartItemRepository.delete(item);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCartItems(User user) {
        Cart cart = getOrCreateCart(user);
        return cart.getItems();
    }

    @Transactional(readOnly = true)
    public BigDecimal getCartTotal(User user) {
        return getCartItems(user).stream()
                .map(item -> item.getPriceAtAddition().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ownership check — CRITICAL security property, not just a convenience helper.
     * Without this, a malicious user could pass ANY cartItemId (e.g. one belonging
     * to a different user, guessed or enumerated) to updateQuantity/removeItem and
     * modify someone else's cart. This is exactly the kind of check ABAC
     * (deliberately deferred elsewhere in this project) would eventually formalize
     * via @PreAuthorize — until that exists, this manual check is what stands in
     * for it on this specific path, and must not be skipped or forgotten on any
     * future cart-item-touching method.
     */
    private CartItem getOwnedCartItem(User user, UUID cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new CartItemNotFoundException(cartItemId.toString()));

        if (!item.getCart().getUser().getId().equals(user.getId())) {
            // Deliberately throw the SAME "not found" exception as a genuinely
            // missing item, rather than a distinct "forbidden" exception — this
            // avoids leaking to an attacker "this ID exists, it's just not yours"
            // vs "this ID doesn't exist at all," the same information-leak
            // reasoning as InvalidCredentialsException's email-enumeration
            // protection earlier in this project.
            throw new CartItemNotFoundException(cartItemId.toString());
        }

        return item;
    }

    /**
     * Soft stock check — confirms enough stock EXISTS at this moment, but does NOT
     * reserve/lock it. A concurrent checkout by another user could still consume
     * that stock between this check and actual checkout. This is deliberate: see
     * this class's own design discussion — real stock reservation is intentionally
     * deferred to the checkout/order-creation path (pessimistic locking, per
     * roadmap doc Section 5), not enforced here at add-to-cart time. Items sitting
     * in a cart do NOT reserve inventory — this matches standard e-commerce UX
     * (adding to cart never locks stock away from other shoppers).
     */
    private void validateStockAvailable(Product product, int requestedQuantity) {
        if (product.getStockQuantity() < requestedQuantity) {
            throw new InsufficientStockException(
                    product.getName(), product.getStockQuantity(), requestedQuantity);
        }
    }
}