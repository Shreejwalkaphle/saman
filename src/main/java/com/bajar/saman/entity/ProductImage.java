package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single image belonging to a product. A product can have many images (gallery),
 * with one designated as the "primary"/thumbnail image.
 *
 * No Auditable, no updatedAt at all — matches V6 migration exactly (product_images
 * only has created_at). This is deliberate: an image row is essentially immutable
 * once created (you don't "edit" an image in place — you'd delete this row and add
 * a new one for a replacement image), so there's no meaningful "last modified"
 * concept here the way there is for Product or User.
 */
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProductImage() {
    }

    public ProductImage(Product product, String imageUrl, boolean primary) {
        this.product = product;
        this.imageUrl = imageUrl;
        this.primary = primary;
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public Product getProduct() { return product; }
    public String getImageUrl() { return imageUrl; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isPrimary() { return primary; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // --- Setters (deliberately limited — imageUrl itself has no setter, matching
    // the "immutable once created" reasoning in this class's own comment above;
    // only display order and primary-flag are realistically things an admin would
    // adjust after the fact, e.g. reordering a gallery or changing which photo is
    // the thumbnail) ---
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public void setPrimary(boolean primary) { this.primary = primary; }
}