package com.bajar.saman.service;

import com.bajar.saman.dto.ShippingAddressRequest;
import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InsufficientStockException;
import com.bajar.saman.exception.IdempotencyConflictException;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.OrderNotFoundException;
import com.bajar.saman.repository.OrderRepository;
import com.bajar.saman.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartService cartService;
    @Mock private com.bajar.saman.service.delivery.DeliveryPartnerFactory deliveryPartnerFactory;

    @InjectMocks
    private OrderService orderService;

    private static final ShippingAddressRequest SHIPPING_ADDRESS = new ShippingAddressRequest(
            "Main Road", null, "Biratnagar", "Morang", null, "9800000000");

    private User buildUser(UUID id) {
        User user = new User("test@example.com", "hashed");
        try {
            java.lang.reflect.Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }

    @Test
    void checkout_withKnownIdempotencyKey_returnsExistingOrderWithoutAnySideEffects() {
        UUID idempotencyKey = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order existingOrder = new Order(user, new BigDecimal("999.99"), idempotencyKey);
        existingOrder.setShippingAddress("Main Road", null, "Biratnagar", "Morang", null, "9800000000");

        when(orderRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingOrder));

        Order result = orderService.checkout(user, idempotencyKey, SHIPPING_ADDRESS);

        assertThat(result).isEqualTo(existingOrder);

        // THE critical assertion set for this test: NOTHING else should have been
        // touched. This is what proves the idempotency check runs first and
        // short-circuits everything else — no cart lookup, no product locking, no
        // save, no delete. If a future refactor accidentally moved the idempotency
        // check to run AFTER any of these, this test would immediately fail.
        verifyNoInteractions(cartService);
        verifyNoInteractions(productRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_withAnotherUsersIdempotencyKey_hidesExistingOrder() {
        UUID key = UUID.randomUUID();
        User owner = buildUser(UUID.randomUUID());
        User attacker = buildUser(UUID.randomUUID());
        Order existing = new Order(owner, new BigDecimal("999.99"), key);
        existing.setShippingAddress("Main Road", null, "Biratnagar", "Morang", null, "9800000000");
        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.checkout(attacker, key, SHIPPING_ADDRESS))
                .isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(cartService, productRepository);
    }

    @Test
    void checkout_withSameKeyButDifferentAddress_rejectsRequestMismatch() {
        UUID key = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Order existing = new Order(user, new BigDecimal("999.99"), key);
        existing.setShippingAddress("Original Road", null, "Biratnagar", "Morang", null, "9800000000");
        when(orderRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.checkout(user, key, SHIPPING_ADDRESS))
                .isInstanceOf(IdempotencyConflictException.class);
        verifyNoInteractions(cartService, productRepository);
    }

    @Test
    void checkout_withEmptyCart_throwsInvalidProductDataException() {
        UUID idempotencyKey = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(cartService.getCartItems(user)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.checkout(user, idempotencyKey, SHIPPING_ADDRESS))
                .isInstanceOf(InvalidProductDataException.class);

        verifyNoInteractions(productRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_withSufficientStock_decrementsStockCreatesOrderAndClearsCart() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());

        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);
        Cart cart = new Cart(user);
        CartItem cartItem = new CartItem(cart, product, 2, new BigDecimal("999.99"));

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(cartService.getCartItems(user)).thenReturn(List.of(cartItem));
        when(productRepository.findByIdForCheckout(product.getId())).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.checkout(user, idempotencyKey, SHIPPING_ADDRESS);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("1999.98"); // 999.99 * 2
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);

        // Stock decrement happens via dirty-checking (same pattern as
        // ProductService.updatePrice / CartService.addItem) — no explicit save()
        // call on productRepository, but the in-memory Product object itself must
        // reflect the decrement, since Hibernate would flush it via the still-open
        // persistence context in a real transaction.
        assertThat(product.getStockQuantity()).isEqualTo(48); // 50 - 2

        // Confirms the CRITICAL lock-acquiring method was used — NOT the plain
        // findById(). Using the wrong method here would silently remove the
        // pessimistic-locking protection this whole feature exists for.
        verify(productRepository).findByIdForCheckout(product.getId());
        verify(productRepository, never()).findById(any());

        verify(cartService).clearCart(user);
    }

    @Test
    void checkout_withInsufficientStockAtLockTime_throwsInsufficientStockException() {
        // This test specifically exercises the RE-CHECK at checkout time — distinct
        // from CartService's own soft check at add-to-cart time. Simulates the
        // real-world scenario the pessimistic lock exists to protect against:
        // stock has dropped below what's in the cart by the time checkout runs
        // (e.g. another user bought the remaining units in between).
        UUID idempotencyKey = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());

        // Product now has only 1 in stock, but the cart item wants 5 — as if stock
        // dropped after this item was added to the cart.
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 1);
        Cart cart = new Cart(user);
        CartItem cartItem = new CartItem(cart, product, 5, new BigDecimal("999.99"));

        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(cartService.getCartItems(user)).thenReturn(List.of(cartItem));
        when(productRepository.findByIdForCheckout(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.checkout(user, idempotencyKey, SHIPPING_ADDRESS))
                .isInstanceOf(InsufficientStockException.class);

        // Nothing should have been persisted or cleared — the whole point of
        // wrapping this in @Transactional is that a mid-loop failure leaves no
        // partial state. (In this unit test, @Transactional rollback itself isn't
        // exercised — that's a Spring-context concern, not something Mockito
        // simulates — but these assertions confirm the CODE PATH itself never
        // reaches the save/delete calls, which is the piece unit-testable here.)
        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    @Test
    void getOrdersForUser_delegatesToRepositoryWithCorrectUserId() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        Order order = new Order(user, new BigDecimal("50.00"), UUID.randomUUID());

        when(orderRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(order));

        List<Order> result = orderService.getOrdersForUser(user);

        assertThat(result).hasSize(1);
        verify(orderRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }
}
