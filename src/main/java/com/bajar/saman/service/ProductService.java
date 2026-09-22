package com.bajar.saman.service;

import com.bajar.saman.entity.Category;
import com.bajar.saman.entity.Product;
import com.bajar.saman.exception.CategoryNotFoundException;
import com.bajar.saman.exception.DuplicateSkuException;
import com.bajar.saman.exception.InvalidProductDataException;
import com.bajar.saman.exception.ProductNotFoundException;
import com.bajar.saman.repository.CategoryRepository;
import com.bajar.saman.repository.ProductRepository;
import com.bajar.saman.repository.UserRoleRepository;
import com.bajar.saman.util.SlugGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRoleRepository userRoleRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          UserRoleRepository userRoleRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional
    public Product createProduct(
            com.bajar.saman.entity.User actor, UUID categoryId, String name, String description,
            BigDecimal price, String sku, int stockQuantity) {

        boolean admin = isAdmin(actor);
        if (!admin && actor.getSellerStatus() != com.bajar.saman.entity.SellerStatus.APPROVED) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only administrators or approved sellers can create products");
        }

        // Validate business rules BEFORE touching the database at all — same
        // "fail fast, cheaply" pattern as UserRegistrationService's
        // existsByEmail-before-hashing check.
        validatePrice(price);
        validateStockQuantity(stockQuantity);

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId.toString()));

        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }

        String uniqueSlug = generateUniqueSlug(name);

        Product product = new Product(category, name, uniqueSlug, price, sku, stockQuantity);
        product.setSeller(admin ? null : actor);
        product.setDescription(description);

        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getProductBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException(slug));
    }

    @Transactional(readOnly = true)
    public Page<Product> listProductsByCategory(UUID categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> listAllProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    @Transactional
    public Product updatePrice(com.bajar.saman.entity.User actor, UUID productId, BigDecimal newPrice) {
        validatePrice(newPrice);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId.toString()));

        requireOwnerOrAdmin(actor, product);

        // No manual save() call needed here — this is "dirty checking," a core
        // Hibernate/JPA behavior we haven't explicitly relied on yet in this
        // project (everywhere else has called repository.save() explicitly).
        // Because this entity was loaded WITHIN this @Transactional method (via
        // findById above) and is still "managed" by Hibernate's persistence
        // context when we call setPrice(), Hibernate automatically detects the
        // change and issues an UPDATE when the transaction commits — no explicit
        // save() required. (Contrast: in AuthenticationService, save() calls WERE
        // needed because those methods pass an already-loaded entity across a
        // method boundary into a DIFFERENT @Transactional method/bean — the
        // persistence context doesn't carry over that way.)
        product.setPrice(newPrice);

        return product;
    }

    @Transactional
    public Product adjustStock(UUID productId, int delta) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId.toString()));

        int newQuantity = product.getStockQuantity() + delta;
        validateStockQuantity(newQuantity);

        product.setStockQuantity(newQuantity);
        return product;

        // NOTE (flagging deliberately, not fixing here): this method is NOT safe
        // against concurrent stock adjustments racing each other in the general
        // sense that roadmap doc Section 5 calls for ("pessimistic locking
        // inventory-decrement path to prevent overselling during checkout").
        // @Version on Product gives OPTIMISTIC locking (a concurrent conflict
        // throws an exception, doesn't silently corrupt data), which is
        // sufficient for admin-panel stock corrections (low concurrency, a retry
        // is an acceptable UX). It is explicitly NOT sufficient for the actual
        // checkout stock-decrement path, which the roadmap correctly identifies
        // as needing PESSIMISTIC locking instead (SELECT ... FOR UPDATE) — that
        // will be built when Cart/Checkout module is reached, not here.
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductDataException("Price must be greater than zero");
        }
    }

    private void validateStockQuantity(int stockQuantity) {
        if (stockQuantity < 0) {
            throw new InvalidProductDataException("Stock quantity cannot be negative");
        }
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = SlugGenerator.generate(name);
        String candidateSlug = baseSlug;
        int suffix = 2;

        while (productRepository.findBySlug(candidateSlug).isPresent()) {
            candidateSlug = baseSlug + "-" + suffix;
            suffix++;
        }

        return candidateSlug;
    }

    private boolean isAdmin(com.bajar.saman.entity.User actor) {
        return userRoleRepository.findRoleNamesByUserId(actor.getId()).contains("ADMIN");
    }

    private void requireOwnerOrAdmin(com.bajar.saman.entity.User actor, Product product) {
        if (isAdmin(actor)) {
            return;
        }

        boolean approvedOwner = actor.getSellerStatus() == com.bajar.saman.entity.SellerStatus.APPROVED
                && product.getSeller() != null
                && product.getSeller().getId().equals(actor.getId());
        if (!approvedOwner) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only modify products that you own");
        }
    }
}
