CREATE TABLE api_key_rotations (
                                   id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                   tenant_id    UUID         NOT NULL,
                                   old_key_hash VARCHAR(64)  NOT NULL,
                                   new_key_hash VARCHAR(64)  NOT NULL,
                                   rotated_by   VARCHAR(255) NOT NULL,
                                   reason       TEXT,
                                   rotated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

                                   CONSTRAINT fk_rotations_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                                   CONSTRAINT chk_different_hashes   CHECK (old_key_hash != new_key_hash)
    );

CREATE INDEX idx_rotations_tenant_id ON api_key_rotations(tenant_id);