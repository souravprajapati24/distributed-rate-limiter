package com.ratelimiter.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UsageResponse(
        UUID    tenantId,
        String  algorithm,
        int     limit,
        int     remaining,
        int     used,
        long    windowResetEpoch,
        Instant asOf
) {}