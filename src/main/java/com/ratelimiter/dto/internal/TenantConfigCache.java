package com.ratelimiter.dto.internal;

import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.entity.QuotaTier;
import com.ratelimiter.domain.entity.TenantQuotaOverride;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TenantConfigCache(
        UUID       tenantId,
        String     tenantName,
        String     apiKeyHash,
        String     status,
        String     failStrategy,
        TierConfig tier,
        List<OverrideConfig> overrides
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


    public record OverrideConfig(
            String endpointPattern,
            int    requestsPerWindow,
            int    windowSizeSeconds,
            String algorithm,
            String limitType
    ) {}


    public static TenantConfigCache from(Tenant tenant, List<TenantQuotaOverride> overrides) {
        QuotaTier tier = tenant.getTier();

        List<OverrideConfig> overrideConfigs = overrides.stream()
                .map(o -> new OverrideConfig(
                        o.getEndpointPattern(),
                        o.getRequestsPerWindow(),
                        o.getWindowSizeSeconds(),
                        o.getAlgorithm() != null ? o.getAlgorithm().name() : null,
                        o.getLimitType() != null ? o.getLimitType().name() : null
                ))
                .toList();

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
                ),
                overrideConfigs
        );
    }
}