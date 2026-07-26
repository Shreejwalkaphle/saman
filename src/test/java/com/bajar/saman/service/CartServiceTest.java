package com.bajar.saman.service;

import com.bajar.saman.entity.Cart;
import com.bajar.saman.entity.CartItem;
import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.User;
import com.bajar.saman.exception.CartItemNotFoundException;
import com.bajar.saman.exception.InsufficientStockException;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.repository.CartItemRepository;
import com.bajar.saman.repository.CartRepository;
import com.bajar.saman.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    private User buildUser(UUID id) {
        User user = new User("test@example.com", "hashed");
        // User has no public ID setter (id is framework/DB-controlled, per the
        // convention established when User was built) — reflection is used here
        // ONLY within test code, specifically to give an in-memory User a
        // deterministic ID for assertions, without needing a real database. This
        // is a normal, accepted use of reflection in test setup — never done in
        // production code in this project. "id" is declared directly on User
        // itself (not inherited from Auditable), so a single getDeclaredField
        // call on User.class is all that's needed here.
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
    void addItem_newProduct_createsNewCartItem() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = buildUser(userId);
        Cart cart = new Cart(user);
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), productId))
                .thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CartItem result = cartService.addItem(user, productId, 2);

        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getPriceAtAddition()).isEqualByComparingTo("999.99");
    }

    @Test
    void addItem_existingProduct_mergesQuantityInsteadOfDuplicating() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = buildUser(userId);
        Cart cart = new Cart(user);
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);
        CartItem existingItem = new CartItem(cart, product, 2, new BigDecimal("999.99"));

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), productId))
                .thenReturn(Optional.of(existingItem));

        CartItem result = cartService.addItem(user, productId, 3);

        assertThat(result.getQuantity()).isEqualTo(5); // 2 + 3, merged
        // Confirms this is the SAME row, updated via dirty-checking — no new
        // save() call, matching the pattern established in ProductService.
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_exceedingStock_throwsInsufficientStockException() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        User user = buildUser(userId);
        Cart cart = new Cart(user);
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 5); // only 5 in stock

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), productId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addItem(user, productId, 10))
                .isInstanceOf(InsufficientStockException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItem_withZeroQuantity_throwsInvalidProductDataException() {
        User user = buildUser(UUID.randomUUID());

        assertThatThrownBy(() -> cartService.addItem(user, UUID.randomUUID(), 0))
                .isInstanceOf(InvalidProductDataException.class);

        // Validated before ANY repository interaction — same fail-fast-and-cheap
        // pattern used throughout this project.
        verifyNoInteractions(productRepository);
        verifyNoInteractions(cartRepository);
    }

    @Test
    void addItem_forInactiveProduct_throwsInvalidProductDataException() {
        UUID productId = UUID.randomUUID();
        User user = buildUser(UUID.randomUUID());
        Product inactiveProduct = new Product(null, "Discontinued Item", "discontinued",
                new BigDecimal("10.00"), "SKU-999", 0);
        inactiveProduct.setActive(false);

        when(productRepository.findById(productId)).thenReturn(Optional.of(inactiveProduct));

        assertThatThrownBy(() -> cartService.addItem(user, productId, 1))
                .isInstanceOf(InvalidProductDataException.class);
    }

    @Test
    void updateQuantity_forItemOwnedByDifferentUser_throwsCartItemNotFoundException() {
        // THIS is the ownership-check test — the security-critical path flagged
        // when CartService was built. User A owns the cart item; User B (a
        // different person entirely) tries to modify it by guessing/reusing its ID.
        User ownerUser = buildUser(UUID.randomUUID());
        User attackerUser = buildUser(UUID.randomUUID()); // a DIFFERENT user

        Cart ownerCart = new Cart(ownerUser);
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);
        CartItem item = new CartItem(ownerCart, product, 1, new BigDecimal("999.99"));
        UUID itemId = UUID.randomUUID();

        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        // attackerUser tries to update OWNER's cart item
        assertThatThrownBy(() -> cartService.updateQuantity(attackerUser, itemId, 5))
                .isInstanceOf(CartItemNotFoundException.class);

        // Critical: the item must NOT actually be mutated when ownership fails —
        // confirms the check happens BEFORE any modification, not after.
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    void updateQuantity_forOwnItem_succeeds() {
        User user = buildUser(UUID.randomUUID());
        Cart cart = new Cart(user);
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);
        CartItem item = new CartItem(cart, product, 1, new BigDecimal("999.99"));
        UUID itemId = UUID.randomUUID();

        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        CartItem result = cartService.updateQuantity(user, itemId, 3);

        assertThat(result.getQuantity()).isEqualTo(3);
    }

    @Test
    void removeItem_forItemOwnedByDifferentUser_throwsCartItemNotFoundException() {
        User ownerUser = buildUser(UUID.randomUUID());
        User attackerUser = buildUser(UUID.randomUUID());
        Cart ownerCart = new Cart(ownerUser);
        Product product = new Product(null, "iPhone 17", "iphone-17",
                new BigDecimal("999.99"), "SKU-001", 50);
        CartItem item = new CartItem(ownerCart, product, 1, new BigDecimal("999.99"));
        UUID itemId = UUID.randomUUID();

        when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.removeItem(attackerUser, itemId))
                .isInstanceOf(CartItemNotFoundException.class);

        // Confirms delete was never actually called against the repository.
        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    void getCartTotal_withMultipleItems_sumsCorrectly() {
        UUID userId = UUID.randomUUID();
        User user = buildUser(userId);
        Cart cart = new Cart(user);
        Product product1 = new Product(null, "Item A", "item-a",
                new BigDecimal("10.00"), "SKU-A", 100);
        Product product2 = new Product(null, "Item B", "item-b",
                new BigDecimal("25.50"), "SKU-B", 100);

        cart.getItems().add(new CartItem(cart, product1, 2, new BigDecimal("10.00")));  // 20.00
        cart.getItems().add(new CartItem(cart, product2, 3, new BigDecimal("25.50")));  // 76.50

        when(cartRepository.findByUserId(userId)).thenReturn(Optional.of(cart));

        BigDecimal total = cartService.getCartTotal(user);

        assertThat(total).isEqualByComparingTo("96.50"); // 20.00 + 76.50
    }
}