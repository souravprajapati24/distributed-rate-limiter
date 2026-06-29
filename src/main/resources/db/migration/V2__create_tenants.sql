CREATE TYPE tenant_status AS ENUM (
    'ACTIVE',
    'SUSPENDED',
    'TRIAL'
);

CREATE TYPE fail_strategy_type AS ENUM (
    'OPEN',
    'CLOSED'
);

CREATE TABLE tenants (
                         id             UUID                NOT NULL DEFAULT gen_random_uuid(),
                         name           VARCHAR(255)        NOT NULL,
                         email          VARCHAR(255)        NOT NULL,
                         api_key_hash   VARCHAR(64)         NOT NULL,
                         tier_id        UUID                NOT NULL,
                         status         tenant_status       NOT NULL DEFAULT 'ACTIVE',
                         fail_strategy  fail_strategy_type  NOT NULL DEFAULT 'OPEN',
                         metadata       JSONB,
                         created_at     TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
                         updated_at     TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
                         suspended_at   TIMESTAMPTZ,
                         suspended_by   VARCHAR(255),

                         CONSTRAINT pk_tenants              PRIMARY KEY (id),
                         CONSTRAINT uq_tenants_api_key_hash UNIQUE (api_key_hash),
                         CONSTRAINT uq_tenants_email        UNIQUE (email),
                         CONSTRAINT fk_tenants_tier_id      FOREIGN KEY (tier_id) REFERENCES quota_tiers(id),

                         CONSTRAINT chk_suspended_fields    CHECK (
                             (status = 'SUSPENDED' AND suspended_at IS NOT NULL)
                                 OR (status != 'SUSPENDED' AND suspended_at IS NULL)
                             )
);

CREATE INDEX idx_tenants_api_key_hash ON tenants(api_key_hash);

CREATE INDEX idx_tenants_tier_id ON tenants(tier_id);

CREATE INDEX idx_tenants_status ON tenants(status) WHERE status != 'ACTIVE';