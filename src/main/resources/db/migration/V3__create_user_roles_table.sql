CREATE TABLE user_roles (
                            user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
                            assigned_at TIMESTAMP NOT NULL DEFAULT now(),

                            PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);