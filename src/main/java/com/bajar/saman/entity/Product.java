package com.bajar.saman.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents a sellable product. Extends Auditable (unlike Category/Role) because
 * products DO need full automatic created_at/updated_at tracking via Spring Data's
 * @CreatedDate/@LastModifiedDate — products change frequently (price updates, stock
 * adjustments, admin edits) and manually remembering to touch updatedAt on every
 * possible mutation (the way Category does above) becomes error-prone as the number
 * of mutable fields grows. Products have more mutable fields than Category, so the
 * automatic approach earns its keep here.
 */
@Entity
@Table(name = "products")
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // fetch = LAZY: loading a Product should not automatically pull in its full
    // Category (and that Category's own parent chain) unless something actually
    // asks for it — same reasoning applied consistently since UserRole.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "description", length = 2000)
    private String description;

    // BigDecimal, never double/float — matches the NUMERIC(10,2) column exactly.
    // Using a Java primitive floating-point type here would reintroduce the exact
    // rounding-error risk the NUMERIC column type was chosen in SQL to avoid — the
    // protection has to hold at BOTH the database layer and the application layer,
    // or it's not actually protection.
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Optimistic locking — identical mechanism and purpose to User.version. Matters
    // even more here than on User: product stock/price edits are exactly the kind
    // of field two concurrent admin actions (or an admin edit racing a checkout
    // stock-decrement, once Cart/Checkout is built) could silently clobber without
    // this.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Product() {
    }

    public Product(Category category, String name, String slug, BigDecimal price,
                   String sku, int stockQuantity) {
        this.category = category;
        this.name = name;
        this.slug = slug;
        this.price = price;
        this.sku = sku;
        this.stockQuantity = stockQuantity;
    }

    // --- Getters ---
    public UUID getId() { return id; }
    public Category getCategory() { return category; }
    public User getSeller() { return seller; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getSku() { return sku; }
    public int getStockQuantity() { return stockQuantity; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }

    // --- Setters ---
    public void setCategory(Category category) { this.category = category; }
    public void setSeller(User seller) { this.seller = seller; }
    public void setName(String name) { this.name = name; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public void setActive(boolean active) { this.active = active; }
}
