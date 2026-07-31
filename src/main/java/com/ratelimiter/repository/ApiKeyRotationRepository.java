package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.ApiKeyRotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApiKeyRotationRepository extends JpaRepository<ApiKeyRotation, UUID> {

    List<ApiKeyRotation> findByTenantIdOrderByRotatedAtDesc(UUID tenantId);

    long countByTenantId(UUID tenantId);
}