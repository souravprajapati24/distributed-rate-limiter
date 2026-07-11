INSERT INTO quota_tiers (
    name, description, algorithm, requests_per_window, window_size_seconds,
    burst_multiplier, leak_rate_per_second, limit_type
) VALUES (
             'INTERNAL_DOWNSTREAM',
             'Leaky Bucket — protects a fragile downstream service with a smoothed 5 req/sec drain rate and a 50-request queue',
             'LEAKY_BUCKET',
             50,
             60,
             1.00,
             5.00,
             'HARD'
         );