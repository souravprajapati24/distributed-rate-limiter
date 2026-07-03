package com.ratelimiter.dto.response;

import com.ratelimiter.domain.entity.UsageSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UsageHistoryResponse(
        UUID       tenantId,
        Instant    periodStart,
        Instant    periodEnd,
        String     granularity,
        long       allowed,
        long       denied,
        long       total,
        BigDecimal denialRate
) {
    public static UsageHistoryResponse from(UsageSummary summary) {
        return new UsageHistoryResponse(
                summary.getTenantId(),
                summary.getPeriodStart(),
                summary.getPeriodEnd(),
                summary.getGranularity().name(),
                summary.getAllowed(),
                summary.getDenied(),
                summary.getTotal(),
                summary.getDenialRate()
        );
    }
}