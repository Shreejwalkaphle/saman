package com.bajar.saman.service;

import com.bajar.saman.entity.*;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.ShopNotFoundException;
import com.bajar.saman.repository.ShopMemberRepository;
import com.bajar.saman.repository.ShopRepository;
import com.bajar.saman.util.SlugGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ShopService {
    private final ShopRepository shopRepository;
    private final ShopMemberRepository memberRepository;

    public ShopService(ShopRepository shopRepository, ShopMemberRepository memberRepository) {
        this.shopRepository = shopRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Shop apply(User owner, UUID applicationKey, String name, String phone, String addressLine1,
                      String city, String district, BigDecimal latitude, BigDecimal longitude) {
        var existing = shopRepository.findByApplicationKey(applicationKey);
        if (existing.isPresent()) {
            Shop shop = existing.get();
            boolean owned = memberRepository.findByShopIdAndUserIdAndActiveTrue(
                    shop.getId(), owner.getId()).isPresent();
            if (!owned) throw new ShopNotFoundException(applicationKey.toString());
            if (!sameApplication(shop, name, phone, addressLine1, city, district, latitude, longitude)) {
                throw new com.bajar.saman.exception.IdempotencyConflictException();
            }
            return shop;
        }
        String slug = uniqueSlug(name);
        Shop shop = shopRepository.save(new Shop(name, slug, applicationKey, phone, addressLine1,
                city, district, latitude, longitude));
        memberRepository.save(new ShopMember(shop, owner, ShopMemberRole.OWNER));
        return shop;
    }

    private boolean sameApplication(Shop shop, String name, String phone, String addressLine1,
                                    String city, String district, BigDecimal latitude, BigDecimal longitude) {
        return shop.getName().equals(name) && shop.getPhone().equals(phone)
                && shop.getAddressLine1().equals(addressLine1) && shop.getCity().equals(city)
                && shop.getDistrict().equals(district)
                && shop.getLatitude().compareTo(latitude) == 0
                && shop.getLongitude().compareTo(longitude) == 0;
    }

    @Transactional(readOnly = true)
    public Page<Shop> listActive(Pageable pageable) {
        return shopRepository.findByStatus(ShopStatus.ACTIVE, pageable);
    }

    @Transactional(readOnly = true)
    public Shop getActiveBySlug(String slug) {
        return shopRepository.findBySlugAndStatus(slug, ShopStatus.ACTIVE)
                .orElseThrow(() -> new ShopNotFoundException(slug));
    }

    @Transactional(readOnly = true)
    public List<Shop> listMine(User user) {
        return memberRepository.findByUserIdAndActiveTrue(user.getId()).stream()
                .map(ShopMember::getShop).toList();
    }

    @Transactional(readOnly = true)
    public Page<Shop> listPending(Pageable pageable) {
        return shopRepository.findByStatus(ShopStatus.PENDING_APPROVAL, pageable);
    }

    @Transactional public Shop approve(UUID id) {
        Shop shop = pending(id); shop.approve(); return shop;
    }
    @Transactional public Shop reject(UUID id, String reason) {
        Shop shop = pending(id); shop.reject(reason); return shop;
    }
    @Transactional public Shop suspend(UUID id, String reason) {
        Shop shop = shopRepository.findById(id).orElseThrow(() -> new ShopNotFoundException(id.toString()));
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new InvalidProductDataException("Only an ACTIVE shop can be suspended");
        }
        shop.suspend(reason); return shop;
    }

    private Shop pending(UUID id) {
        Shop shop = shopRepository.findById(id).orElseThrow(() -> new ShopNotFoundException(id.toString()));
        if (shop.getStatus() != ShopStatus.PENDING_APPROVAL) {
            throw new InvalidProductDataException("Shop is not pending approval (status: " + shop.getStatus() + ")");
        }
        return shop;
    }

    private String uniqueSlug(String name) {
        String base = SlugGenerator.generate(name); String candidate = base; int suffix = 2;
        while (shopRepository.findBySlug(candidate).isPresent()) candidate = base + "-" + suffix++;
        return candidate;
    }
}
