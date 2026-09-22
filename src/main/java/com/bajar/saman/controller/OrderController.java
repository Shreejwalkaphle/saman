package com.bajar.saman.controller;

import com.bajar.saman.dto.OrderResponse;
import com.bajar.saman.entity.Order;
import com.bajar.saman.entity.User;
import com.bajar.saman.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders/checkout
     *
     * The idempotency key comes from a REQUIRED custom header, "Idempotency-Key" —
     * not the request body. This matches industry convention (Stripe and similar
     * payment/checkout APIs do the same) since the key is request METADATA, not
     * business data — the actual order content comes entirely from the user's
     * current server-side cart, nothing else needs to be in the body at all.
     *
     * required = true: a checkout request with NO idempotency key is rejected
     * outright (400, via Spring's own MissingRequestHeaderException, already
     * covered generically by GlobalExceptionHandler's catch-all — a dedicated
     * handler could be added later if a more specific message is wanted). This is
     * deliberate: silently proceeding without a key would defeat the entire
     * purpose of the feature — better to fail loudly than to accept an insecure
     * checkout request.
     */
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @AuthenticationPrincipal User user,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @jakarta.validation.Valid @RequestBody com.bajar.saman.dto.ShippingAddressRequest shippingAddress) {

        Order order = orderService.checkout(user, idempotencyKey, shippingAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(order));
    }

    /**
     * Admin-facing shipping endpoints. @PreAuthorize matches the same
     * ADMIN-only pattern established for Catalog mutation (Category/Product
     * controllers) — shipping/delivery status changes are exactly the kind
     * of admin action that pattern exists for.
     */
    @PatchMapping("/{orderId}/dispatch")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> dispatch(
            @PathVariable UUID orderId,
            @RequestParam com.bajar.saman.entity.DeliveryPartnerType deliveryPartner) {
        Order order = orderService.dispatchOrder(orderId, deliveryPartner);
        return ResponseEntity.ok(toResponse(order));
    }

    @PatchMapping("/{orderId}/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> advanceStatus(
            @PathVariable UUID orderId,
            @RequestParam com.bajar.saman.entity.OrderStatus newStatus) {
        Order order = orderService.advanceDeliveryStatus(orderId, newStatus);
        return ResponseEntity.ok(toResponse(order));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal User user) {
        List<OrderResponse> response = orderService.getOrdersForUser(user)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getPriceAtPurchase(),
                        item.getQuantity()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items,
                order.getShippingCity(),
                order.getTrackingNumber(),
                order.getShippedAt(),
                order.getDeliveredAt()
        );
    }
}