package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.UsageSummary;
import com.ratelimiter.domain.enums.GranularityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsageSummaryRepository extends JpaRepository<UsageSummary, UUID> {

    Page<UsageSummary> findByTenantIdAndGranularityAndPeriodStartBetween(
            UUID tenantId,
            GranularityType granularity,
            Instant from,
            Instant to,
            Pageable pageable
    );

    Optional<UsageSummary> findByTenantIdAndPeriodStartAndGranularity(
            UUID tenantId,
            Instant periodStart,
            GranularityType granularity
    );
}