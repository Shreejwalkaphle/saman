package com.bajar.saman.repository;

import com.bajar.saman.entity.Shop;
import com.bajar.saman.entity.ShopStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends JpaRepository<Shop, UUID> {
    Optional<Shop> findBySlugAndStatus(String slug, ShopStatus status);
    Optional<Shop> findBySlug(String slug);
    Optional<Shop> findByApplicationKey(UUID applicationKey);
    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);
}
