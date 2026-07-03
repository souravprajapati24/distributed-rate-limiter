package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.enums.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {


    Optional<Tenant> findByApiKeyHash(String apiKeyHash);

    List<Tenant> findByTierId(UUID tierId);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE t.status = 'ACTIVE' AND t.tier.id = :tierId")
    List<Tenant> findActiveByTierId(@Param("tierId") UUID tierId);

    @Query("SELECT t FROM Tenant t JOIN FETCH t.tier WHERE t.apiKeyHash = :apiKeyHash")
    Optional<Tenant> findByApiKeyHashWithTier(@Param("apiKeyHash") String apiKeyHash);

    boolean existsByEmail(String email);
}