-- Categories table: supports hierarchical (parent -> child) structure via a
-- self-referencing foreign key. A root-level category (e.g. "Electronics") has
-- parent_id = NULL. A subcategory (e.g. "Mobile Phones" under "Electronics")
-- points its parent_id at Electronics' own id.
CREATE TABLE categories (
                            id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            name           VARCHAR(100) NOT NULL,
                            slug           VARCHAR(120) NOT NULL UNIQUE,
                            description    VARCHAR(500),

    -- Self-referencing FK. ON DELETE RESTRICT (not CASCADE): deliberately prevents
    -- deleting a parent category while it still has children — forces an explicit
    -- decision (reassign or delete children first) rather than silently cascading
    -- a delete through an entire category tree by accident. This mirrors the same
    -- reasoning as roles.id -> user_roles.role_id being RESTRICT in the Auth module.
                            parent_id      UUID REFERENCES categories(id) ON DELETE RESTRICT,

                            display_order  INT NOT NULL DEFAULT 0,

                            created_at     TIMESTAMP NOT NULL DEFAULT now(),
                            updated_at     TIMESTAMP NOT NULL DEFAULT now()
);

-- Every category listing page needs "find all children of category X" — this index
-- makes that a fast lookup instead of a full table scan, and matters more here than
-- in most tables since category trees get queried on nearly every storefront page
-- load (nav menus, breadcrumbs).
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- slug is how a category is looked up from a URL (e.g. /category/mobile-phones) —
-- this index makes that lookup fast. UNIQUE constraint above already creates an
-- index automatically in Postgres, actually — this explicit CREATE INDEX line is
-- redundant with the UNIQUE constraint and is intentionally left OUT (noting this
-- here so a future reader doesn't wonder why slug doesn't get its own extra index
-- like parent_id does).