package com.bajar.saman.repository;

import com.bajar.saman.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * THE pessimistic-locking method roadmap doc Section 5 called for by name —
     * "pessimistic locking inventory-decrement path to prevent overselling during
     * checkout." @Lock(PESSIMISTIC_WRITE) translates to Postgres's
     * `SELECT ... FOR UPDATE` — this makes any OTHER transaction that tries to
     * read (with a lock) or write this same product row BLOCK until this
     * transaction commits or rolls back. This is deliberately a DIFFERENT
     * mechanism from Product's own @Version optimistic locking (used everywhere
     * else, e.g. ProductService.updatePrice): optimistic locking lets two
     * transactions proceed concurrently and only fails one of them AFTER the
     * fact if they conflict; pessimistic locking here PREVENTS the second
     * transaction from even reading the row until the first is done — the
     * stronger guarantee needed specifically for "don't let two simultaneous
     * checkouts both think there's enough stock when there isn't."
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForCheckout(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Product> findBySlug(String slug);

    boolean existsBySku(String sku);

    // Pageable in, Page<Product> out — this is the core pagination pattern. The
    // CALLER (service layer, eventually a controller) decides page size/number via
    // the Pageable argument; Spring Data JPA translates that into a SQL query with
    // LIMIT/OFFSET automatically, and the returned Page object carries both the
    // requested slice of results AND metadata (total elements, total pages) needed
    // to render "Page 3 of 47" style UI — without us writing a separate COUNT(*)
    // query by hand.
    //
    // Only returns ACTIVE products — deliberately baked into the query itself
    // (not filtered afterward in Java) so an inactive/hidden product can never
    // accidentally leak into a customer-facing listing, and so the LIMIT/OFFSET
    // pagination math stays correct against the actual filtered result set (an
    // application-level filter applied AFTER pagination would silently return
    // fewer items than the requested page size, and break "total pages" math).
    Page<Product> findByCategoryIdAndActiveTrue(UUID categoryId, Pageable pageable);

    // All active products, no category filter — for a general "browse everything"
    // storefront view.
    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);
}
