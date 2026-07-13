CREATE TABLE roles (
                       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       name        VARCHAR(50) NOT NULL UNIQUE,
                       description VARCHAR(255),
                       created_at  TIMESTAMP NOT NULL DEFAULT now()

);

INSERT INTO roles (name, description) VALUES
                                          ('CUSTOMER', 'Regular shopper who can browse and place orders'),
                                          ('SELLER', 'Can list and manage their own products'),
                                          ('ADMIN', 'Full system access, user and platform management'),
                                          ('SUPPORT', 'Customer support access, limited admin capabilities');