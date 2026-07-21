package com.ratelimiter.domain.entity;

import com.ratelimiter.domain.enums.DecisionType;
import com.ratelimiter.dto.internal.AuditEvent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;

import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_limit_audit_log")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false, length = 500)
    private String endpoint;

    @Column(name = "http_method", nullable = false, updatable = false, length = 10)
    private String httpMethod;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "decision_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DecisionType decision;


    @Column(name = "algorithm_used", nullable = false, updatable = false, length = 30)
    private String algorithmUsed;

    @Column(name = "counter_value", nullable = false, updatable = false)
    private int counterValue;

    @Column(name = "limit_value", nullable = false, updatable = false)
    private int limitValue;

    @Column(nullable = false, updatable = false)
    private int remaining;

    @Column(name = "limit_type", nullable = false, updatable = false, length = 10)
    private String limitType;

    @Column(name = "window_start", updatable = false)
    private Instant windowStart;

    @Column(name = "window_end", updatable = false)
    private Instant windowEnd;

    @Column(name = "client_ip", updatable = false, length = 45)
    private String clientIp;

    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;


    @PrePersist
    protected void onCreate() {
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }
    }


    public static RateLimitAuditLog from(AuditEvent event) {
        return RateLimitAuditLog.builder()
                .tenantId(event.tenantId())
                .endpoint(event.endpoint())
                .httpMethod(event.httpMethod())
                .decision(DecisionType.valueOf(event.decision()))
                .algorithmUsed(event.algorithmUsed())
                .counterValue(event.counterValue())
                .limitValue(event.limitValue())
                .remaining(event.remaining())
                .limitType(event.limitType())
                .windowStart(event.windowStart())
                .windowEnd(event.windowEnd())
                .clientIp(event.clientIp())
                .evaluatedAt(event.evaluatedAt())
                .build();
    }
}