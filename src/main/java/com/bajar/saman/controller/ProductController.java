package com.bajar.saman.controller;

import com.bajar.saman.dto.CategoryResponse;
import com.bajar.saman.dto.CreateProductRequest;
import com.bajar.saman.dto.PageResponse;
import com.bajar.saman.dto.ProductResponse;
import com.bajar.saman.dto.UpdateProductRequest;
import com.bajar.saman.entity.Product;
import com.bajar.saman.entity.User;
import com.bajar.saman.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> create(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user,
            @Valid @RequestBody CreateProductRequest request) {
        Product product = productService.createProduct(
                user, request.shopId(), request.categoryId(), request.name(), request.description(),
                request.price(), request.sku(), request.stockQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(product));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProductResponse> getBySlug(@PathVariable String slug) {
        Product product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(toResponse(product));
    }

    // @PageableDefault: if the client doesn't specify page/size/sort query params
    // (e.g. just calls GET /api/products with no params), these defaults apply —
    // page 20 items, sorted by name ascending. Spring Data Web automatically binds
    // query params like ?page=2&size=10&sort=price,desc into this Pageable object
    // without us writing any parsing code ourselves.
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> listAll(
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<Product> products = productService.listAllProducts(pageable);
        return ResponseEntity.ok(PageResponse.from(products.map(this::toResponse)));
    }

    @GetMapping("/shop/{shopId}/manage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<ProductResponse>> listForManagement(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user,
            @PathVariable UUID shopId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                productService.listShopProducts(user, shopId, pageable).map(this::toResponse)));
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<PageResponse<ProductResponse>> listForShop(
            @PathVariable UUID shopId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                productService.listActiveProductsForShop(shopId, pageable).map(this::toResponse)));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<PageResponse<ProductResponse>> listByCategory(
            @PathVariable UUID categoryId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        Page<Product> products = productService.listProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(PageResponse.from(products.map(this::toResponse)));
    }

    @PatchMapping("/{id}/price")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> updatePrice(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user,
            @PathVariable UUID id, @RequestParam java.math.BigDecimal newPrice) {
        Product product = productService.updatePrice(user, id, newPrice);
        return ResponseEntity.ok(toResponse(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProductResponse> update(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user,
            @PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        Product product = productService.updateProduct(user, id, request.categoryId(),
                request.name(), request.description(), request.price(), request.stockQuantity());
        return ResponseEntity.ok(toResponse(product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deactivate(
            @org.springframework.security.core.annotation.AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        productService.deactivateProduct(user, id);
        return ResponseEntity.noContent().build();
    }

    private ProductResponse toResponse(Product product) {
        CategoryResponse categoryResponse = new CategoryResponse(
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getCategory().getSlug(),
                product.getCategory().getDescription(),
                product.getCategory().getParent() != null
                        ? product.getCategory().getParent().getId() : null
        );

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getPrice(),
                product.getSku(),
                product.getStockQuantity(),
                product.isActive(),
                product.getShop().getId(),
                product.getShop().getName(),
                categoryResponse
        );
    }
}
