package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.exception.*;
import com.bajar.saman.repository.CartItemRepository;
import com.bajar.saman.repository.OrderRepository;
import com.bajar.saman.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            CartItemRepository cartItemRepository,
            CartService cartService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartService = cartService;
    }

    /**
     * Creates an order from the user's current cart. This is THE most concurrency-
     * sensitive method in the whole project so far — read every comment below
     * carefully, this is where roadmap doc Section 5's pessimistic-locking
     * requirement and this session's idempotency-key addition both actually do
     * their job.
     */
    @Transactional
    public Order checkout(User user, UUID idempotencyKey) {

        // ---- STEP 1: Idempotency check — MUST be first, before anything else ----
        // If a request with this exact key already succeeded (e.g. the client's
        // first attempt actually worked, but the response was lost to a network
        // timeout and it retried), return the SAME order unchanged rather than
        // creating a second one. This is what actually prevents double-charging/
        // double-ordering on retry — the entire reason this column was added.
        var existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            return existingOrder.get();
        }

        // ---- STEP 2: Load the cart, reject if empty ----
        List<CartItem> cartItems = cartService.getCartItems(user);
        if (cartItems.isEmpty()) {
            throw new InvalidProductDataException("Cannot check out an empty cart");
        }

        BigDecimal total = BigDecimal.ZERO;
        Order order = new Order(user, BigDecimal.ZERO, idempotencyKey); // total filled in below

        // ---- STEP 3: For EACH cart item — lock, validate, decrement, snapshot ----
        for (CartItem cartItem : cartItems) {

            // findByIdForCheckout uses PESSIMISTIC_WRITE (SELECT ... FOR UPDATE,
            // per ProductRepository's own comment). This BLOCKS any other
            // transaction from reading/writing this same product row until THIS
            // transaction commits or rolls back — this is what actually prevents
            // two simultaneous checkouts from both seeing "50 in stock" and both
            // successfully decrementing, overselling the product. This is a
            // fundamentally stronger guarantee than Product's own @Version
            // optimistic locking (used everywhere else, e.g.
            // ProductService.adjustStock) — optimistic locking would let both
            // transactions proceed and only fail one AFTER the fact; pessimistic
            // locking here prevents the second one from even reading the row
            // until the first is fully done.
            Product product = productRepository.findByIdForCheckout(cartItem.getProduct().getId())
                    .orElseThrow(() -> new ProductNotFoundException(cartItem.getProduct().getId().toString()));

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                // Re-check stock HERE, at checkout time, even though CartService
                // already soft-checked it at add-to-cart time. Time has passed
                // since the item was added — another user could have bought the
                // remaining stock in the meantime. This is the REAL enforcement
                // point; the earlier check in CartService was explicitly just a
                // UX nicety, not a guarantee (see that class's own comment).
                throw new InsufficientStockException(
                        product.getName(), product.getStockQuantity(), cartItem.getQuantity());
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());
            // No explicit save() needed — dirty checking handles the UPDATE,
            // same pattern established in ProductService.updatePrice, and this
            // entity was loaded within THIS same transaction/method so the
            // persistence context is still attached.

            OrderItem orderItem = new OrderItem(
                    order, product, product.getName(), cartItem.getPriceAtAddition(), cartItem.getQuantity());
            order.getItems().add(orderItem);

            total = total.add(cartItem.getPriceAtAddition()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        // Now that the real total is known (after the loop), fix it on the Order
        // via reflection-free direct field access isn't possible here since there's
        // no setter — using a small package-private-style workaround: rebuild via
        // the constructor isn't ideal either. Simplest correct fix: give Order a
        // package-private/internal setter for this one case (see Order.java note
        // below) rather than fight the entity's own immutability conventions.
        order.setTotalAmount(total);

        Order savedOrder = orderRepository.save(order);

        // ---- STEP 4: Clear the cart — checkout succeeded, nothing should remain ----
        for (CartItem cartItem : cartItems) {
            cartItemRepository.delete(cartItem);
        }

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersForUser(User user) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }
}