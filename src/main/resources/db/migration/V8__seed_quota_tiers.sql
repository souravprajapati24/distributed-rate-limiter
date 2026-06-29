INSERT INTO quota_tiers (name, description, algorithm, requests_per_window, window_size_seconds, burst_multiplier, limit_type)
VALUES
    ('FREE',
     'Free tier — 100 req/min, Fixed Window, hard limit',
     'FIXED_WINDOW',
     100,
     60,
     1.00,
     'HARD'),

    ('STARTER',
     'Starter tier — 1,000 req/min, Sliding Window, hard limit',
     'SLIDING_WINDOW',
     1000,
     60,
     1.00,
     'HARD'),

    ('GROWTH',
     'Growth tier — 10,000 req/min, Token Bucket with 2x burst, hard limit',
     'TOKEN_BUCKET',
     10000,
     60,
     2.00,
     'HARD'),

    ('ENTERPRISE',
     'Enterprise tier — 100,000 req/min, Token Bucket with 3x burst, soft limit',
     'TOKEN_BUCKET',
     100000,
     60,
     3.00,
     'SOFT');