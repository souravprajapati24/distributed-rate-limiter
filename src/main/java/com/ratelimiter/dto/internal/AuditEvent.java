package com.ratelimiter.dto.internal;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID    eventId,
        UUID    tenantId,
        String  endpoint,
        String  httpMethod,
        String  decision,
        String  algorithmUsed,
        int     counterValue,
        int     limitValue,
        int     remaining,
        String  limitType,
        Instant windowStart,
        Instant windowEnd,
        String  clientIp,
        Instant evaluatedAt
) {
    public static AuditEvent from(TenantConfigCache tenant, RateLimitDecision decision,
                                  String effectiveDecision,
                                  String effectiveLimitType,
                                  String endpoint,
                                  String httpMethod,
                                  String clientIp) {
        return new AuditEvent(
                UUID.randomUUID(),
                tenant.tenantId(),
                endpoint,
                httpMethod,
                effectiveDecision,
                decision.algorithm(),
                decision.limit() - decision.remaining(),
                decision.limit(),
                decision.remaining(),
                effectiveLimitType,
                null,
                Instant.ofEpochSecond(decision.resetAtEpochSecond()),
                clientIp,
                Instant.now()
        );
    }
}