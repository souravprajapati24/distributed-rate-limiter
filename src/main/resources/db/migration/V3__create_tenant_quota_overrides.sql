CREATE TABLE tenant_quota_overrides (
                                        id                   UUID                   PRIMARY KEY DEFAULT gen_random_uuid(),
                                        tenant_id            UUID                   NOT NULL,
                                        endpoint_pattern     VARCHAR(255)           NOT NULL,
                                        requests_per_window  INTEGER                NOT NULL,
                                        window_size_seconds  INTEGER                NOT NULL,
                                        algorithm            algorithm_type,
                                        limit_type           limit_enforcement_type,
                                        is_active            BOOLEAN                NOT NULL DEFAULT TRUE,
                                        created_at           TIMESTAMPTZ            NOT NULL DEFAULT NOW(),
                                        updated_at           TIMESTAMPTZ            NOT NULL DEFAULT NOW(),

                                        CONSTRAINT fk_overrides_tenant_id  FOREIGN KEY (tenant_id) REFERENCES tenants(id),

                                        CONSTRAINT uq_overrides_tenant_ep  UNIQUE (tenant_id, endpoint_pattern),
                                        CONSTRAINT chk_override_requests   CHECK (requests_per_window > 0),
                                        CONSTRAINT chk_override_window     CHECK (window_size_seconds > 0)
);

CREATE INDEX idx_overrides_tenant_id ON tenant_quota_overrides(tenant_id);