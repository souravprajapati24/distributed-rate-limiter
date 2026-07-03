package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.TenantQuotaOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantQuotaOverrideRepository extends JpaRepository<TenantQuotaOverride, UUID> {

    List<TenantQuotaOverride> findByTenantIdAndActiveTrue(UUID tenantId);

    Optional<TenantQuotaOverride> findByTenantIdAndEndpointPattern(UUID tenantId, String endpointPattern);

    void deleteByTenantId(UUID tenantId);
}