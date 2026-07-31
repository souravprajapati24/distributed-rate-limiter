CREATE TABLE admin_users (
                             id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                             username      VARCHAR(255) NOT NULL,
                             email         VARCHAR(255) NOT NULL,
                             password_hash VARCHAR(60)  NOT NULL,
                             role          VARCHAR(20)  NOT NULL DEFAULT 'ROLE_OPERATOR',
                             is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
                             created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                             last_login_at TIMESTAMPTZ,

                             CONSTRAINT uq_admin_users_username UNIQUE (username),
                             CONSTRAINT uq_admin_users_email    UNIQUE (email),
                             CONSTRAINT chk_admin_role          CHECK (role IN ('ROLE_ADMIN', 'ROLE_OPERATOR'))
);

CREATE INDEX idx_admin_users_username ON admin_users(username);
CREATE INDEX idx_admin_users_email    ON admin_users(email);