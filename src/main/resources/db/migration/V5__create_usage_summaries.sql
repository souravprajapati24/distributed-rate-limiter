CREATE TYPE granularity_type AS ENUM (
    'HOURLY',
    'DAILY'
);

CREATE TABLE usage_summaries (
                                 id           UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
                                 tenant_id    UUID              NOT NULL,
                                 period_start TIMESTAMPTZ       NOT NULL,
                                 period_end   TIMESTAMPTZ       NOT NULL,
                                 granularity  granularity_type  NOT NULL,
                                 allowed      BIGINT            NOT NULL DEFAULT 0,
                                 denied       BIGINT            NOT NULL DEFAULT 0,

                                 total        BIGINT            GENERATED ALWAYS AS (allowed + denied) STORED,
                                 denial_rate  DECIMAL(5, 2)     GENERATED ALWAYS AS (
                                     CASE WHEN (allowed + denied) = 0 THEN 0.00
                                          ELSE ROUND((denied::DECIMAL / (allowed + denied)) * 100, 2)
                                         END
                                     ) STORED,

                                 created_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
                                 updated_at   TIMESTAMPTZ       NOT NULL DEFAULT NOW(),

                                 CONSTRAINT fk_usage_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                                 CONSTRAINT uq_usage_period    UNIQUE (tenant_id, period_start, granularity),
                                 CONSTRAINT chk_period_order   CHECK (period_end > period_start),
                                 CONSTRAINT chk_allowed_count  CHECK (allowed >= 0),
                                 CONSTRAINT chk_denied_count   CHECK (denied >= 0)
);

CREATE INDEX idx_usage_tenant_id    ON usage_summaries(tenant_id);

CREATE INDEX idx_usage_period_start ON usage_summaries(tenant_id, period_start DESC, granularity);