package com.bajar.saman.controller;

import com.bajar.saman.dto.*;
import com.bajar.saman.entity.Shop;
import com.bajar.saman.service.ShopService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/admin/shops") @PreAuthorize("hasRole('ADMIN')")
public class ShopAdminController {
    private final ShopService service;
    public ShopAdminController(ShopService service) { this.service = service; }
    @GetMapping("/pending") public PageResponse<ShopResponse> pending(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(service.listPending(pageable).map(this::response));
    }
    @PatchMapping("/{id}/approve") public ShopResponse approve(@PathVariable UUID id) {
        return response(service.approve(id));
    }
    @PatchMapping("/{id}/reject") public ShopResponse reject(@PathVariable UUID id,
            @Valid @RequestBody RejectShopRequest request) {
        return response(service.reject(id, request.reason()));
    }
    @PatchMapping("/{id}/suspend") public ShopResponse suspend(@PathVariable UUID id,
            @Valid @RequestBody RejectShopRequest request) {
        return response(service.suspend(id, request.reason()));
    }
    private ShopResponse response(Shop s) {
        return new ShopResponse(s.getId(), s.getName(), s.getSlug(), s.getPhone(),
                s.getAddressLine1(), s.getCity(), s.getDistrict(), s.getLatitude(),
                s.getLongitude(), s.getStatus().name(), s.getRejectionReason());
    }
}
