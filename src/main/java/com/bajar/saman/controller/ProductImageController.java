package com.bajar.saman.controller;

import com.bajar.saman.entity.ProductImage;
import com.bajar.saman.service.ProductImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    // consumes = MULTIPART_FORM_DATA_VALUE: tells Spring this endpoint expects a
    // file upload (multipart/form-data), not JSON — without this, Spring wouldn't
    // know how to bind the incoming request into a MultipartFile parameter.
    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductImageResponse> upload(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "primary", defaultValue = "false") boolean primary) {

        ProductImage image = productImageService.uploadImage(productId, file, primary);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(image));
    }

    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> list(@PathVariable UUID productId) {
        List<ProductImageResponse> response = productImageService.getImagesForProduct(productId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    private ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(
                image.getId(), image.getImageUrl(), image.getDisplayOrder(), image.isPrimary());
    }

    // Small enough (4 fields, single use) that a nested record here is reasonable
    // rather than a separate top-level DTO file — consistent with keeping the
    // project from accumulating trivial one-off files, while still keeping every
    // OTHER DTO in the dedicated dto package as established.
    private record ProductImageResponse(UUID id, String imageUrl, int displayOrder, boolean primary) {
    }
}