package com.bajar.saman.controller;

import com.bajar.saman.dto.*;
import com.bajar.saman.entity.CartItem;
import com.bajar.saman.entity.User;
import com.bajar.saman.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // @AuthenticationPrincipal injects whatever object JwtAuthenticationFilter put
    // as the "principal" when it built the UsernamePasswordAuthenticationToken —
    // which, by deliberate design back when that filter was built, is the full
    // User entity itself (not just a username string). No manual
    // SecurityContextHolder lookup needed here — Spring wires this automatically
    // for every method on this controller (and any other) that declares it.
    @GetMapping
    public ResponseEntity<CartResponse> getCart(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(buildCartResponse(user));
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addItem(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AddCartItemRequest request) {
        CartItem item = cartService.addItem(user, request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(item));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<CartItemResponse> updateQuantity(
            @AuthenticationPrincipal User user,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateQuantityRequest request) {
        CartItem item = cartService.updateQuantity(user, itemId, request.quantity());
        return ResponseEntity.ok(toResponse(item));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @AuthenticationPrincipal User user,
            @PathVariable UUID itemId) {
        cartService.removeItem(user, itemId);
        // 204 No Content: the HTTP-correct status for "the operation succeeded,
        // there is deliberately nothing to return" — a delete operation has no
        // meaningful response body.
        return ResponseEntity.noContent().build();
    }

    private CartResponse buildCartResponse(User user) {
        List<CartItemResponse> items = cartService.getCartItems(user).stream()
                .map(this::toResponse).toList();
        BigDecimal total = cartService.getCartTotal(user);
        return new CartResponse(items, total);
    }

    private CartItemResponse toResponse(CartItem item) {
        BigDecimal lineTotal = item.getPriceAtAddition()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getPriceAtAddition(),
                lineTotal
        );
    }
}