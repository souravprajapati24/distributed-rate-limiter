package com.ratelimiter.dto.response;

import com.ratelimiter.domain.entity.QuotaTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuotaTierResponse(
        UUID       id,
        String     name,
        String     description,
        String     algorithm,
        int        requestsPerWindow,
        int        windowSizeSeconds,
        BigDecimal burstMultiplier,
        BigDecimal leakRatePerSecond,
        String     limitType,
        boolean    active,
        Instant    createdAt,
        Instant    updatedAt
) {
    public static QuotaTierResponse from(QuotaTier tier) {
        return new QuotaTierResponse(
                tier.getId(),
                tier.getName(),
                tier.getDescription(),
                tier.getAlgorithm().name(),
                tier.getRequestsPerWindow(),
                tier.getWindowSizeSeconds(),
                tier.getBurstMultiplier(),
                tier.getLeakRatePerSecond(),
                tier.getLimitType().name(),
                tier.isActive(),
                tier.getCreatedAt(),
                tier.getUpdatedAt()
        );
    }
}