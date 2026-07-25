CREATE TABLE product_images (
                                id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ON DELETE CASCADE here, UNLIKE the RESTRICT choices above — deliberately
    -- different reasoning: images have no independent meaning without their
    -- product. If a product is ever actually deleted, its images should go with
    -- it automatically — there's no scenario where you'd want to keep orphaned
    -- images around after their product is gone (unlike categories/products,
    -- where losing the parent silently orphans genuinely meaningful child data).
                                product_id     UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,

                                image_url      VARCHAR(500) NOT NULL,
                                display_order  INT NOT NULL DEFAULT 0,
                                is_primary     BOOLEAN NOT NULL DEFAULT false,

                                created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);

-- DELIBERATELY NOT ADDED YET (flagging for PROGRESS.md, not fixing now): there is
-- no database-level constraint enforcing "at most one is_primary=true image per
-- product." Postgres CAN enforce this via a partial unique index
-- (CREATE UNIQUE INDEX ... ON product_images(product_id) WHERE is_primary = true),
-- but that's being deferred until the actual image-upload service is built in a
-- later step — enforcing it at the application/service layer first, matching this
-- project's established pattern of get-it-working-then-harden rather than
-- front-loading every constraint before there's real usage to inform it.