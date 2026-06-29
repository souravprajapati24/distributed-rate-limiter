CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE algorithm_type AS ENUM (
    'FIXED_WINDOW',
    'SLIDING_WINDOW',
    'TOKEN_BUCKET',
    'LEAKY_BUCKET'
);

CREATE TYPE limit_enforcement_type AS ENUM (
    'HARD',
    'SOFT'
);

CREATE TABLE quota_tiers (
                             id                   UUID                   PRIMARY KEY DEFAULT gen_random_uuid(),
                             name                 VARCHAR(100)           NOT NULL,
                             description          TEXT,
                             algorithm            algorithm_type         NOT NULL,
                             requests_per_window  INTEGER                NOT NULL,
                             window_size_seconds  INTEGER                NOT NULL,
                             burst_multiplier     DECIMAL(4, 2)          NOT NULL DEFAULT 1.00,
                             leak_rate_per_second DECIMAL(10, 2),
                             limit_type           limit_enforcement_type NOT NULL DEFAULT 'HARD',
                             is_active            BOOLEAN                NOT NULL DEFAULT TRUE,
                             created_at           TIMESTAMPTZ            NOT NULL DEFAULT NOW(),
                             updated_at           TIMESTAMPTZ            NOT NULL DEFAULT NOW(),

                             CONSTRAINT uq_quota_tiers_name        UNIQUE (name),
                             CONSTRAINT chk_requests_per_window    CHECK (requests_per_window > 0),
                             CONSTRAINT chk_window_size_seconds    CHECK (window_size_seconds > 0 AND window_size_seconds <= 86400),
                             CONSTRAINT chk_burst_multiplier       CHECK (burst_multiplier >= 1.00 AND burst_multiplier <= 100.00),
                             CONSTRAINT chk_leak_rate              CHECK (leak_rate_per_second IS NULL OR leak_rate_per_second > 0)
);

