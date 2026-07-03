package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.RateLimitAuditLog;
import com.ratelimiter.domain.enums.DecisionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface RateLimitAuditLogRepository extends JpaRepository<RateLimitAuditLog, UUID> {

    Page<RateLimitAuditLog> findByTenantIdAndDecisionAndEvaluatedAtBetween(
            UUID tenantId,
            DecisionType decision,
            Instant from,
            Instant to,
            Pageable pageable
    );


    Page<RateLimitAuditLog> findByTenantIdAndEvaluatedAtBetween(
            UUID tenantId,
            Instant from,
            Instant to,
            Pageable pageable
    );

    Page<RateLimitAuditLog> findByDecisionAndEvaluatedAtBetween(
            DecisionType decision,
            Instant from,
            Instant to,
            Pageable pageable
    );


    @Query("""
        SELECT a.tenantId, a.decision, COUNT(a)
        FROM RateLimitAuditLog a
        WHERE a.evaluatedAt >= :from AND a.evaluatedAt < :to
        GROUP BY a.tenantId, a.decision
        """)
    List<Object[]> aggregateByTenantAndDecision(
            @Param("from") Instant from,
            @Param("to")   Instant to
    );
}