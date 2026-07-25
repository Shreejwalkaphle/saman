package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Represents a product category, supporting unlimited hierarchy depth via a
 * self-referencing parent relationship (e.g. Electronics -> Mobile Phones ->
 * Smartphones). A root-level category has parent = null.
 *
 * Deliberately does NOT extend Auditable — categories table only has created_at/
 * updated_at as plain columns (matches V4 migration), same situation as Role: kept
 * standalone rather than sharing the Auditable base class. (Auditable is reserved
 * for entities where BOTH timestamps need Spring Data's automatic @CreatedDate/
 * @LastModifiedDate management — here we're fine setting them at construction time
 * and via explicit updates instead, consistent with how Role was built.)
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    // Deliberately unidirectional (see class-level comment above for the full
    // reasoning) — this entity knows its OWN parent, but does not hold a list of
    // its children. "Give me all children of category X" is answered via
    // CategoryRepository.findByParentId(UUID), not by navigating this field.
    //
    // fetch = LAZY: same reasoning as UserRole's relationships — loading a
    // Category should not automatically trigger loading its entire ancestor chain
    // unless something actually asks for it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    protected Category() {
    }

    public Category(String name, String slug, Category parent) {
        this.name = name;
        this.slug = slug;
        this.parent = parent; // null is valid here — means "root-level category"
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public Category getParent() { return parent; }
    public int getDisplayOrder() { return displayOrder; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }

    // --- Setters (business-logic-mutable fields only — id/createdAt excluded,
    // same convention established throughout this project) ---
    public void setName(String name) {
        this.name = name;
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public void setSlug(String slug) {
        this.slug = slug;
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public void setParent(Category parent) {
        this.parent = parent;
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
        this.updatedAt = java.time.LocalDateTime.now();
    }
}