CREATE TABLE carts (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ON DELETE CASCADE: if a user account is ever deleted, their cart (which has
    -- no meaning without them) should go too — same reasoning as product_images ->
    -- products. UNIQUE: enforces "one active cart per user" at the DB level, not
    -- just application logic.
                       user_id     UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

                       created_at  TIMESTAMP NOT NULL DEFAULT now(),
                       updated_at  TIMESTAMP NOT NULL DEFAULT now()
);