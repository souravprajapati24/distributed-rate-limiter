package com.ratelimiter.dto.response;

import com.ratelimiter.domain.entity.RateLimitAuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID    id,
        UUID    tenantId,
        String  endpoint,
        String  httpMethod,
        String  decision,
        String  algorithmUsed,
        int     counterValue,
        int     limitValue,
        int     remaining,
        String  limitType,
        String  clientIp,
        Instant evaluatedAt
) {
    public static AuditLogResponse from(RateLimitAuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getTenantId(),
                log.getEndpoint(),
                log.getHttpMethod(),
                log.getDecision().name(),
                log.getAlgorithmUsed(),
                log.getCounterValue(),
                log.getLimitValue(),
                log.getRemaining(),
                log.getLimitType(),
                log.getClientIp(),
                log.getEvaluatedAt()
        );
    }
}