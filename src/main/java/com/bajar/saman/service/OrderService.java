package com.bajar.saman.service;

import com.bajar.saman.dto.ShippingAddressRequest;
import com.bajar.saman.entity.*;
import com.bajar.saman.exception.*;
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
    private final CartService cartService;
    private final com.bajar.saman.service.delivery.DeliveryPartnerFactory deliveryPartnerFactory;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            CartService cartService,
            com.bajar.saman.service.delivery.DeliveryPartnerFactory deliveryPartnerFactory) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
        this.deliveryPartnerFactory = deliveryPartnerFactory;
    }

    /**
     * Creates an order from the user's current cart. This is THE most concurrency-
     * sensitive method in the whole project so far — read every comment below
     * carefully, this is where roadmap doc Section 5's pessimistic-locking
     * requirement and this session's idempotency-key addition both actually do
     * their job.
     */
    @Transactional
    public Order checkout(User user, UUID idempotencyKey, ShippingAddressRequest shippingAddress) {

        // ---- STEP 1: Idempotency check — MUST be first, before anything else ----
        // If a request with this exact key already succeeded (e.g. the client's
        // first attempt actually worked, but the response was lost to a network
        // timeout and it retried), return the SAME order unchanged rather than
        // creating a second one. This is what actually prevents double-charging/
        // double-ordering on retry — the entire reason this column was added.
        var existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
        if (existingOrder.isPresent()) {
            Order existing = existingOrder.get();
            if (!existing.getUser().getId().equals(user.getId())) {
                throw new OrderNotFoundException(idempotencyKey.toString());
            }
            if (!sameShippingAddress(existing, shippingAddress)) {
                throw new IdempotencyConflictException();
            }
            return existing;
        }

        // ---- STEP 2: Load the cart, reject if empty ----
        List<CartItem> cartItems = new java.util.ArrayList<>(cartService.getCartItems(user));
        if (cartItems.isEmpty()) {
            throw new InvalidProductDataException("Cannot check out an empty cart");
        }

        // Every transaction locks product rows in the same deterministic order.
        // This prevents two multi-product checkouts (or checkout versus expiry)
        // from taking opposite lock orders and deadlocking each other.
        cartItems.sort(java.util.Comparator.comparing(item -> item.getProduct().getId()));

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

            // Closes gap #2 tracked in PROGRESS.md (§6, HIGH priority):
            // re-checking stock alone was insufficient — a product an admin
            // deactivated AFTER it was added to a cart still had a valid
            // stock count and could still be checked out. This check runs
            // under the SAME pessimistic lock already acquired above
            // (findByIdForCheckout), so it's consistent with a concurrent
            // admin deactivation the same way the stock check already is —
            // no separate locking concern introduced.
            if (!product.isActive()) {
                throw new InvalidProductDataException(
                        "'" + product.getName() + "' is no longer available for purchase");
            }

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
        // Address is captured here, at order-creation time, snapshotted onto
        // the Order itself — see V14 migration's comment for why this isn't
        // a live reference to a user profile address (which doesn't exist
        // yet anyway — deliberately deferred "saved addresses" feature).
        order.setShippingAddress(
                shippingAddress.addressLine1(), shippingAddress.addressLine2(),
                shippingAddress.city(), shippingAddress.district(),
                shippingAddress.postalCode(), shippingAddress.phone());

        Order savedOrder = orderRepository.save(order);

        // ---- STEP 4: Clear the cart — checkout succeeded, nothing should remain ----
        cartService.clearCart(user);

        return savedOrder;
    }

    private boolean sameShippingAddress(Order order, ShippingAddressRequest request) {
        return java.util.Objects.equals(order.getShippingAddressLine1(), request.addressLine1())
                && java.util.Objects.equals(order.getShippingAddressLine2(), request.addressLine2())
                && java.util.Objects.equals(order.getShippingCity(), request.city())
                && java.util.Objects.equals(order.getShippingDistrict(), request.district())
                && java.util.Objects.equals(order.getShippingPostalCode(), request.postalCode())
                && java.util.Objects.equals(order.getShippingPhone(), request.phone());
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersForUser(User user) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    /**
     * Admin-facing: marks an order as shipped. Only valid from PAID status —
     * shipping an unpaid order (or re-shipping an already-shipped one) is a
     * business-rule violation, not a technical error, hence
     * InvalidProductDataException (same exception type used for other
     * business-rule violations throughout this project, e.g.
     * ProductService's price/stock validation).
     */
    /**
     * Roadmap Addendum v2 §2.2/§2.3: legal FORWARD transitions in the
     * delivery pipeline. A Map<OrderStatus, Set<OrderStatus>> — key is the
     * CURRENT status, value is the set of statuses it's legal to move to
     * from there. Any transition not listed here is rejected. This
     * generalizes the single-status-guard pattern already used elsewhere
     * in this project (Order's original PAID-before-ship check,
     * PaymentService's terminal-status check) into an explicit, reviewable
     * table rather than scattered if-checks — appropriate now that there
     * are enough stages that ad-hoc checks would become error-prone.
     */
    private static final java.util.Map<OrderStatus, java.util.Set<OrderStatus>> ALLOWED_TRANSITIONS = java.util.Map.of(
            OrderStatus.SHIPPED_FROM_WAREHOUSE, java.util.Set.of(OrderStatus.IN_TRANSIT, OrderStatus.DELIVERY_FAILED),
            OrderStatus.IN_TRANSIT, java.util.Set.of(OrderStatus.ARRIVED_AT_LOCAL_HUB, OrderStatus.DELIVERY_FAILED),
            OrderStatus.ARRIVED_AT_LOCAL_HUB, java.util.Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERY_FAILED),
            OrderStatus.OUT_FOR_DELIVERY, java.util.Set.of(OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED)
    );

    /**
     * Dispatches an order from the warehouse — the ONE transition that
     * requires selecting a real delivery partner (via the Strategy+Factory
     * pattern, same as Payment). Only legal from PAID.
     */
    @Transactional
    public Order dispatchOrder(UUID orderId, com.bajar.saman.entity.DeliveryPartnerType partnerType) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvalidProductDataException(
                    "Order must be PAID before it can be dispatched (current status: " + order.getStatus() + ")");
        }

        com.bajar.saman.service.delivery.DeliveryPartner partner = deliveryPartnerFactory.getPartner(partnerType);
        String trackingNumber = partner.initiateShipment(order);

        order.dispatchFromWarehouse(partnerType.name(), trackingNumber);
        return order;
    }

    /**
     * Advances an order through any LATER pipeline stage
     * (IN_TRANSIT → ARRIVED_AT_LOCAL_HUB → OUT_FOR_DELIVERY → DELIVERED, or
     * → DELIVERY_FAILED from any of those). Validated against
     * ALLOWED_TRANSITIONS — an out-of-sequence jump (e.g. SHIPPED_FROM_WAREHOUSE
     * straight to DELIVERED, skipping intermediate stages) is rejected, not
     * silently allowed.
     */
    @Transactional
    public Order advanceDeliveryStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));

        java.util.Set<OrderStatus> allowedNext = ALLOWED_TRANSITIONS.get(order.getStatus());
        if (allowedNext == null || !allowedNext.contains(newStatus)) {
            throw new InvalidProductDataException(
                    "Cannot move order from " + order.getStatus() + " to " + newStatus);
        }

        order.advanceDeliveryStatus(newStatus);
        return order;
    }
}
