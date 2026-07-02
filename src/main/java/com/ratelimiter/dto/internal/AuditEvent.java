package com.ratelimiter.dto.internal;

import jakarta.servlet.http.HttpServletRequest;
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
                                  HttpServletRequest request) {
        return new AuditEvent(
                UUID.randomUUID(),
                tenant.tenantId(),
                request.getRequestURI(),
                request.getMethod(),
                decision.allowed() ? "ALLOWED" : "DENIED",
                decision.algorithm(),
                decision.limit() - decision.remaining(),
                decision.limit(),
                decision.remaining(),
                tenant.tier().limitType(),
                null,
                Instant.ofEpochSecond(decision.resetAtEpochSecond()),
                request.getRemoteAddr(),
                Instant.now()
        );
    }
}