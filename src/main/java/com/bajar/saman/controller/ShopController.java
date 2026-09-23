package com.bajar.saman.controller;

import com.bajar.saman.dto.*;
import com.bajar.saman.entity.Shop;
import com.bajar.saman.entity.User;
import com.bajar.saman.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/shops")
public class ShopController {
    private final ShopService service;
    public ShopController(ShopService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ShopResponse> apply(@AuthenticationPrincipal User user,
                                               @RequestHeader("Idempotency-Key") java.util.UUID applicationKey,
                                               @Valid @RequestBody CreateShopRequest request) {
        Shop shop = service.apply(user, applicationKey, request.name(), request.phone(), request.addressLine1(),
                request.city(), request.district(), request.latitude(), request.longitude());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(shop));
    }
    @GetMapping public PageResponse<ShopResponse> list(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return PageResponse.from(service.listActive(pageable).map(this::toResponse));
    }
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/mine") public List<ShopResponse> mine(@AuthenticationPrincipal User user) {
        return service.listMine(user).stream().map(this::toResponse).toList();
    }
    @GetMapping("/{slug}") public ShopResponse bySlug(@PathVariable String slug) {
        return toResponse(service.getActiveBySlug(slug));
    }
    private ShopResponse toResponse(Shop s) {
        return new ShopResponse(s.getId(), s.getName(), s.getSlug(), s.getPhone(),
                s.getAddressLine1(), s.getCity(), s.getDistrict(), s.getLatitude(),
                s.getLongitude(), s.getStatus().name(), s.getRejectionReason());
    }
}
