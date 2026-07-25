CREATE TABLE products (
                          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ON DELETE RESTRICT again — deliberately prevents deleting a category that
    -- still has products in it. Forces an explicit product-reassignment or
    -- deletion decision first, rather than silently orphaning or cascading.
                          category_id     UUID NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,

                          name            VARCHAR(200) NOT NULL,
                          slug            VARCHAR(220) NOT NULL UNIQUE,
                          description     VARCHAR(2000),

    -- NUMERIC, never FLOAT/DOUBLE, for money — floating point can't represent most
    -- decimal fractions exactly (0.1 + 0.2 != 0.3 in binary floating point), which
    -- is unacceptable for currency. NUMERIC(10,2) = up to 8 digits before the
    -- decimal point + 2 after (max value ~99,999,999.99), which comfortably covers
    -- any realistic product price.
                          price           NUMERIC(10, 2) NOT NULL,

    -- SKU: Stock Keeping Unit — a human/warehouse-facing unique product code,
    -- SEPARATE from the UUID primary key. The UUID is for internal system
    -- references (foreign keys, API URLs); SKU is what a warehouse worker or
    -- admin panel would actually type/scan to identify a physical item. Standard
    -- e-commerce/inventory practice to have both.
                          sku             VARCHAR(50) NOT NULL UNIQUE,

                          stock_quantity  INT NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),

    -- Soft-delete / availability toggle pattern — same reasoning as users.is_active
    -- in the Auth module: an admin can hide a product from the storefront without
    -- destroying its order history, reviews, or other data that references it.
                          is_active       BOOLEAN NOT NULL DEFAULT true,

    -- Optimistic locking column, identical purpose to users.version — prevents two
    -- concurrent admin edits (or an admin edit racing a checkout stock-decrement)
    -- from silently overwriting each other. Directly matches what roadmap doc
    -- Section 5 called for on product edits.
                          version         BIGINT NOT NULL DEFAULT 0,

                          created_at      TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- Composite index on (category_id, price): this is the EXACT pattern roadmap doc
-- Section 5 called out by name ("composite indexes on frequently filtered/sorted
-- columns e.g. product(category_id, price)") — supports the single most common
-- storefront query shape: "show me products in category X, sorted/filtered by
-- price." A single-column index on category_id alone would still need a separate
-- sort step for price; this composite index lets Postgres satisfy both the filter
-- AND the sort from one index.
CREATE INDEX idx_products_category_id_price ON products(category_id, price);

-- Separate index for the "only show active products" filter, which will be in
-- nearly every storefront-facing query (customers should never see is_active=false
-- products) — a partial index (WHERE is_active = true) is smaller and faster than
-- indexing the whole boolean column, since Postgres only needs to index the rows
-- storefront queries actually care about.
CREATE INDEX idx_products_active ON products(id) WHERE is_active = true;