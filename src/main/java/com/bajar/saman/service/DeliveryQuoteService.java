package com.bajar.saman.service;

import com.bajar.saman.dto.CreateDeliveryQuoteRequest;
import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.repository.DeliveryQuoteRepository;
import com.bajar.saman.repository.DeliveryZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryQuoteService {
    private static final double EARTH_RADIUS_KM = 6371.0088;
    private final DeliveryZoneRepository zoneRepository;
    private final DeliveryQuoteRepository quoteRepository;
    private final CartService cartService;

    public DeliveryQuoteService(DeliveryZoneRepository zoneRepository,
                                DeliveryQuoteRepository quoteRepository,
                                CartService cartService) {
        this.zoneRepository = zoneRepository;
        this.quoteRepository = quoteRepository;
        this.cartService = cartService;
    }

    @Transactional
    public DeliveryQuote create(User user, CreateDeliveryQuoteRequest request) {
        List<CartItem> items = cartService.getCartItems(user);
        if (items.isEmpty()) throw new InvalidProductDataException("Cannot quote delivery for an empty cart");
        Shop shop = items.getFirst().getProduct().getShop();
        if (items.stream().anyMatch(i -> !i.getProduct().getShop().getId().equals(shop.getId())))
            throw new InvalidProductDataException("A delivery quote supports products from one shop only");

        DeliveryZone zone = zoneRepository.findAllByActiveTrue().stream()
                .filter(candidate -> candidate.getCity().equalsIgnoreCase(request.city().trim()))
                .filter(candidate -> candidate.getDistrict().equalsIgnoreCase(request.district().trim()))
                .filter(candidate -> distance(candidate.getCenterLatitude(), candidate.getCenterLongitude(),
                        request.latitude(), request.longitude()).compareTo(candidate.getServiceRadiusKm()) <= 0)
                .findFirst()
                .orElseThrow(() -> new InvalidProductDataException(
                        "The selected location is outside the currently supported delivery zones"));

        BigDecimal routeDistance = distance(shop.getLatitude(), shop.getLongitude(),
                request.latitude(), request.longitude());
        if (routeDistance.compareTo(zone.getMaxDeliveryDistanceKm()) > 0)
            throw new InvalidProductDataException("This shop is more than "
                    + zone.getMaxDeliveryDistanceKm().stripTrailingZeros().toPlainString()
                    + " km from the delivery location");

        BigDecimal fee = calculateFee(routeDistance, zone);
        DeliveryQuote quote = new DeliveryQuote(user, shop, zone, request.latitude(), request.longitude(),
                routeDistance, fee, LocalDateTime.now().plusMinutes(zone.getQuoteValidityMinutes()));
        return quoteRepository.save(quote);
    }

    @Transactional
    public DeliveryQuote claim(User user, UUID quoteId, UUID shopId,
                               BigDecimal latitude, BigDecimal longitude) {
        DeliveryQuote quote = quoteRepository.findByIdForCheckout(quoteId)
                .orElseThrow(() -> new InvalidProductDataException("Delivery quote is invalid or unavailable"));
        if (!quote.getUser().getId().equals(user.getId()))
            throw new InvalidProductDataException("Delivery quote is invalid or unavailable");
        if (quote.getUsedAt() != null) throw new InvalidProductDataException("Delivery quote has already been used");
        if (!quote.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new InvalidProductDataException("Delivery quote has expired; request a new quote");
        if (!quote.getShop().getId().equals(shopId))
            throw new InvalidProductDataException("Delivery quote does not match the cart shop");
        if (quote.getCustomerLatitude().compareTo(latitude) != 0 ||
                quote.getCustomerLongitude().compareTo(longitude) != 0)
            throw new InvalidProductDataException("Delivery quote does not match the shipping location");
        quote.markUsed();
        return quote;
    }

    static BigDecimal calculateFee(BigDecimal distanceKm, DeliveryZone zone) {
        BigDecimal extra = distanceKm.subtract(zone.getBaseDistanceKm());
        if (extra.signum() <= 0) return zone.getBaseFee().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal chargedExtraKm = extra.setScale(0, RoundingMode.CEILING);
        return zone.getBaseFee().add(chargedExtraKm.multiply(zone.getAdditionalFeePerKm()))
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal distance(BigDecimal fromLat, BigDecimal fromLon,
                               BigDecimal toLat, BigDecimal toLon) {
        double lat1 = Math.toRadians(fromLat.doubleValue());
        double lat2 = Math.toRadians(toLat.doubleValue());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(toLon.doubleValue() - fromLon.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP);
    }
}
