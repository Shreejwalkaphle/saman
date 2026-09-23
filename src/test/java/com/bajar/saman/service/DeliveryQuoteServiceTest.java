package com.bajar.saman.service;

import com.bajar.saman.dto.CreateDeliveryQuoteRequest;
import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.repository.DeliveryQuoteRepository;
import com.bajar.saman.repository.DeliveryZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryQuoteServiceTest {
    @Mock DeliveryZoneRepository zoneRepository;
    @Mock DeliveryQuoteRepository quoteRepository;
    @Mock CartService cartService;
    @InjectMocks DeliveryQuoteService service;

    @Test
    void feeUsesBaseThenChargesEachStartedAdditionalKilometre() {
        DeliveryZone zone = zone();
        assertThat(DeliveryQuoteService.calculateFee(new BigDecimal("3.000"), zone)).isEqualByComparingTo("30.00");
        assertThat(DeliveryQuoteService.calculateFee(new BigDecimal("3.001"), zone)).isEqualByComparingTo("40.00");
        assertThat(DeliveryQuoteService.calculateFee(new BigDecimal("9.999"), zone)).isEqualByComparingTo("100.00");
    }

    @Test
    void createRejectsTextAddressOutsidePilotCityBeforeSaving() {
        User user = user(UUID.randomUUID());
        when(cartService.getCartItems(user)).thenReturn(List.of(cartItem(user, shop())));
        when(zoneRepository.findAllByActiveTrue()).thenReturn(List.of(zone()));

        assertThatThrownBy(() -> service.create(user,
                new CreateDeliveryQuoteRequest(new BigDecimal("26.45"), new BigDecimal("87.27"), "Dharan", "Sunsari")))
                .isInstanceOf(InvalidProductDataException.class)
                .hasMessageContaining("outside the currently supported");
        verify(quoteRepository, never()).save(any());
    }

    @Test
    void createPersistsServerCalculatedQuoteForCartShop() {
        User user = user(UUID.randomUUID());
        Shop shop = shop();
        when(cartService.getCartItems(user)).thenReturn(List.of(cartItem(user, shop)));
        when(zoneRepository.findAllByActiveTrue()).thenReturn(List.of(zone()));
        when(quoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DeliveryQuote quote = service.create(user,
                new CreateDeliveryQuoteRequest(new BigDecimal("26.452500"), new BigDecimal("87.271800"),
                        "Biratnagar", "Morang"));

        assertThat(quote.getShop()).isSameAs(shop);
        assertThat(quote.getDistanceKm()).isEqualByComparingTo("0.000");
        assertThat(quote.getFee()).isEqualByComparingTo("30.00");
        assertThat(quote.getPricingVersion()).isEqualTo(1);
    }

    @Test
    void claimRejectsQuoteOwnedByAnotherUserWithoutLeakingOwnership() {
        User owner = user(UUID.randomUUID());
        User attacker = user(UUID.randomUUID());
        Shop shop = shop();
        DeliveryQuote quote = quote(owner, shop, LocalDateTime.now().plusMinutes(5));
        UUID quoteId = UUID.randomUUID();
        when(quoteRepository.findByIdForCheckout(quoteId)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.claim(attacker, quoteId, shop.getId(),
                quote.getCustomerLatitude(), quote.getCustomerLongitude()))
                .isInstanceOf(InvalidProductDataException.class)
                .hasMessage("Delivery quote is invalid or unavailable");
        assertThat(quote.getUsedAt()).isNull();
    }

    @Test
    void claimRejectsExpiredQuote() {
        User user = user(UUID.randomUUID());
        Shop shop = shop();
        DeliveryQuote quote = quote(user, shop, LocalDateTime.now().minusSeconds(1));
        UUID quoteId = UUID.randomUUID();
        when(quoteRepository.findByIdForCheckout(quoteId)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.claim(user, quoteId, shop.getId(),
                quote.getCustomerLatitude(), quote.getCustomerLongitude()))
                .isInstanceOf(InvalidProductDataException.class).hasMessageContaining("expired");
    }

    private DeliveryZone zone() {
        DeliveryZone zone = org.springframework.beans.BeanUtils.instantiateClass(DeliveryZone.class);
        set(zone, "id", UUID.randomUUID()); set(zone, "code", "BIRATNAGAR_PILOT");
        set(zone, "name", "Biratnagar Pilot Zone"); set(zone, "city", "Biratnagar");
        set(zone, "district", "Morang"); set(zone, "centerLatitude", new BigDecimal("26.452500"));
        set(zone, "centerLongitude", new BigDecimal("87.271800"));
        set(zone, "serviceRadiusKm", new BigDecimal("10.00"));
        set(zone, "maxDeliveryDistanceKm", new BigDecimal("10.00"));
        set(zone, "baseDistanceKm", new BigDecimal("3.00")); set(zone, "baseFee", new BigDecimal("30.00"));
        set(zone, "additionalFeePerKm", new BigDecimal("10.00")); set(zone, "quoteValidityMinutes", 10);
        set(zone, "pricingVersion", 1); set(zone, "active", true); return zone;
    }

    private DeliveryQuote quote(User user, Shop shop, LocalDateTime expiresAt) {
        return new DeliveryQuote(user, shop, zone(), new BigDecimal("26.452500"),
                new BigDecimal("87.271800"), BigDecimal.ZERO.setScale(3), new BigDecimal("30.00"), expiresAt);
    }

    private Shop shop() {
        Shop shop = new Shop("Shop", "shop-" + UUID.randomUUID(), UUID.randomUUID(), "9800000000",
                "Main Road", "Biratnagar", "Morang", new BigDecimal("26.452500"), new BigDecimal("87.271800"));
        set(shop, "id", UUID.randomUUID()); return shop;
    }

    private CartItem cartItem(User user, Shop shop) {
        Product product = new Product(null, "Item", "item-" + UUID.randomUUID(), BigDecimal.TEN, "SKU-" + UUID.randomUUID(), 5);
        product.setShop(shop); return new CartItem(new Cart(user), product, 1, BigDecimal.TEN);
    }

    private User user(UUID id) { User user = new User("u" + id + "@test.local", "hash"); set(user, "id", id); return user; }
    private void set(Object target, String field, Object value) { ReflectionTestUtils.setField(target, field, value); }
}
