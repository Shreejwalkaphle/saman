package com.bajar.saman.repository;

import com.bajar.saman.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    // Ordered by displayOrder — "OrderBy" is another Spring Data JPA query-method
    // keyword, translates to SQL's ORDER BY. Ensures a product's image gallery
    // always comes back in the admin-configured display sequence, not in
    // arbitrary/insertion order (which Postgres does not guarantee without an
    // explicit ORDER BY).
    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(UUID productId);
}