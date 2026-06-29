CREATE TYPE decision_type AS ENUM (
    'ALLOWED',
    'DENIED'
);

CREATE TABLE rate_limit_audit_log (
                                      id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                      tenant_id      UUID           NOT NULL,
                                      endpoint       VARCHAR(500)   NOT NULL,
                                      http_method    VARCHAR(10)    NOT NULL,
                                      decision       decision_type  NOT NULL,
                                      algorithm_used VARCHAR(30)    NOT NULL,
                                      counter_value  INTEGER        NOT NULL,
                                      limit_value    INTEGER        NOT NULL,
                                      remaining      INTEGER        NOT NULL,
                                      limit_type     VARCHAR(10)    NOT NULL,
                                      window_start   TIMESTAMPTZ,
                                      window_end     TIMESTAMPTZ,
                                      client_ip      INET,
                                      user_agent     VARCHAR(500),
                                      evaluated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

                                      CONSTRAINT fk_audit_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(id),
                                      CONSTRAINT chk_counter_value  CHECK (counter_value >= 0),
                                      CONSTRAINT chk_limit_value    CHECK (limit_value > 0),
                                      CONSTRAINT chk_remaining      CHECK (remaining >= 0)
);

CREATE INDEX idx_audit_tenant_id ON rate_limit_audit_log(tenant_id);


CREATE INDEX idx_audit_evaluated_at ON rate_limit_audit_log(evaluated_at DESC);

CREATE INDEX idx_audit_decision ON rate_limit_audit_log(decision);

CREATE INDEX idx_audit_tenant_decision ON rate_limit_audit_log(tenant_id, decision, evaluated_at DESC);