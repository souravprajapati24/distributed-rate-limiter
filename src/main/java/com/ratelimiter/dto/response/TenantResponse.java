package com.ratelimiter.dto.response;

import com.ratelimiter.domain.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID    id,
        String  name,
        String  email,
        String  apiKey,
        UUID    tierId,
        String  tierName,
        String  algorithm,
        String  status,
        String  failStrategy,
        Instant createdAt,
        Instant suspendedAt
) {

    public static TenantResponse withApiKey(Tenant tenant, String plaintextApiKey) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getEmail(),
                plaintextApiKey,
                tenant.getTier().getId(),
                tenant.getTier().getName(),
                tenant.getTier().getAlgorithm().name(),
                tenant.getStatus().name(),
                tenant.getFailStrategy().name(),
                tenant.getCreatedAt(),
                tenant.getSuspendedAt()
        );
    }


    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getEmail(),
                null,
                tenant.getTier().getId(),
                tenant.getTier().getName(),
                tenant.getTier().getAlgorithm().name(),
                tenant.getStatus().name(),
                tenant.getFailStrategy().name(),
                tenant.getCreatedAt(),
                tenant.getSuspendedAt()
        );
    }
}