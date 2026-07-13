CREATE TABLE users (
                       id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       email                  VARCHAR(255) NOT NULL UNIQUE,
                       password_hash          VARCHAR(255),

                       is_email_verified      BOOLEAN NOT NULL DEFAULT false,
                       is_active              BOOLEAN NOT NULL DEFAULT true,
                       failed_login_attempts  INT NOT NULL DEFAULT 0,
                       locked_until           TIMESTAMP,

                       mfa_enabled            BOOLEAN NOT NULL DEFAULT false,
                       mfa_secret             VARCHAR(255),

                       created_at             TIMESTAMP NOT NULL DEFAULT now(),
                       updated_at             TIMESTAMP NOT NULL DEFAULT now(),
                       version                BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON users(email);