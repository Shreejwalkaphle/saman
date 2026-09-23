package com.bajar.saman.controller;

import com.bajar.saman.dto.CreateDeliveryQuoteRequest;
import com.bajar.saman.dto.DeliveryQuoteResponse;
import com.bajar.saman.entity.DeliveryQuote;
import com.bajar.saman.entity.User;
import com.bajar.saman.service.DeliveryQuoteService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/delivery/quotes")
public class DeliveryQuoteController {
    private final DeliveryQuoteService service;
    public DeliveryQuoteController(DeliveryQuoteService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<DeliveryQuoteResponse> create(@AuthenticationPrincipal User user,
                                                        @Valid @RequestBody CreateDeliveryQuoteRequest request) {
        DeliveryQuote quote = service.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DeliveryQuoteResponse(
                quote.getId(), quote.getShop().getId(), quote.getZone().getCode(), quote.getZone().getName(),
                quote.getDistanceKm(), quote.getFee(), quote.getCurrency(), quote.getPricingVersion(),
                quote.getExpiresAt()));
    }
}
