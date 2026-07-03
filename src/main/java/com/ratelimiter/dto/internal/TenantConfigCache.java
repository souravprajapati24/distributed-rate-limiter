package com.ratelimiter.dto.internal;

import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.entity.QuotaTier;

import java.math.BigDecimal;
import java.util.UUID;
public record TenantConfigCache(
        UUID      tenantId,
        String    tenantName,
        String    apiKeyHash,
        String    status,
        String    failStrategy,
        TierConfig tier
) {

    public record TierConfig(
            UUID       tierId,
            String     algorithm,
            int        requestsPerWindow,
            int        windowSizeSeconds,
            BigDecimal burstMultiplier,
            BigDecimal leakRatePerSecond,
            String     limitType
    ) {}


    public static TenantConfigCache from(Tenant tenant) {
        QuotaTier tier = tenant.getTier();
        return new TenantConfigCache(
                tenant.getId(),
                tenant.getName(),
                tenant.getApiKeyHash(),
                tenant.getStatus().name(),
                tenant.getFailStrategy().name(),
                new TierConfig(
                        tier.getId(),
                        tier.getAlgorithm().name(),
                        tier.getRequestsPerWindow(),
                        tier.getWindowSizeSeconds(),
                        tier.getBurstMultiplier(),
                        tier.getLeakRatePerSecond(),
                        tier.getLimitType().name()
                )
        );
    }
}