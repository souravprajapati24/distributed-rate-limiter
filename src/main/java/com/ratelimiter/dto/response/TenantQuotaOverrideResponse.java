package com.ratelimiter.dto.response;

import com.ratelimiter.domain.entity.TenantQuotaOverride;

import java.time.Instant;
import java.util.UUID;


public record TenantQuotaOverrideResponse(
        UUID    id,
        UUID    tenantId,
        String  endpointPattern,
        int     requestsPerWindow,
        int     windowSizeSeconds,
        String  algorithm,
        String  limitType,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static TenantQuotaOverrideResponse from(TenantQuotaOverride override) {
        return new TenantQuotaOverrideResponse(
                override.getId(),
                override.getTenant().getId(),
                override.getEndpointPattern(),
                override.getRequestsPerWindow(),
                override.getWindowSizeSeconds(),
                override.getAlgorithm() != null ? override.getAlgorithm().name() : null,
                override.getLimitType() != null ? override.getLimitType().name() : null,
                override.isActive(),
                override.getCreatedAt(),
                override.getUpdatedAt()
        );
    }
}