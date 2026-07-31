package com.ratelimiter.dto.response;

import java.time.Instant;
import java.util.UUID;

public record KeyRotationResponse(
        UUID    tenantId,
        String  tenantName,
        String  newApiKey,
        String  oldKeyHashPrefix,
        String  rotatedBy,
        String  reason,
        Instant rotatedAt
) {}