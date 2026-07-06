package com.ratelimiter.service;

import com.ratelimiter.domain.entity.QuotaTier;
import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.enums.FailStrategyType;
import com.ratelimiter.dto.internal.TenantConfigCache;
import com.ratelimiter.dto.request.TierAssignRequest;
import com.ratelimiter.dto.request.TenantRequest;
import com.ratelimiter.dto.response.TenantResponse;
import com.ratelimiter.exception.DuplicateTenantException;
import com.ratelimiter.exception.TenantNotFoundException;
import com.ratelimiter.exception.TierNotFoundException;
import com.ratelimiter.repository.TenantRepository;
import com.ratelimiter.repository.QuotaTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final QuotaTierRepository quotaTierRepository;
    private final TenantCacheService tenantCacheService;


    @Transactional
    public TenantResponse registerTenant(TenantRequest request) {

        QuotaTier tier = quotaTierRepository.findById(request.tierId())
                .orElseThrow(() -> new TierNotFoundException(request.tierId()));

        if (!tier.isActive()) {
            throw new IllegalArgumentException("Cannot assign tenant to inactive tier: " + tier.getName());
        }

        if (tenantRepository.existsByEmail(request.email())) {
            throw new DuplicateTenantException("Tenant with email '" + request.email() + "' already exists");
        }

        String plaintextApiKey = generateApiKey();
        String apiKeyHash = hashApiKey(plaintextApiKey);

        FailStrategyType failStrategy = FailStrategyType.OPEN;
        if (request.failStrategy() != null && !request.failStrategy().isBlank()) {
            try {
                failStrategy = FailStrategyType.valueOf(request.failStrategy().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid fail strategy: " + request.failStrategy() + ". Must be OPEN or CLOSED.");
            }
        }

        Tenant tenant = Tenant.builder()
                .name(request.name())
                .email(request.email())
                .apiKeyHash(apiKeyHash)
                .tier(tier)
                .failStrategy(failStrategy)
                .build();

        tenant = tenantRepository.save(tenant);

        tenantCacheService.put(apiKeyHash, TenantConfigCache.from(tenant));
        log.info("Registered new tenant: {} (ID: {}, Tier: {})", tenant.getName(), tenant.getId(), tier.getName());

        return TenantResponse.withApiKey(tenant, plaintextApiKey);
    }

    @Transactional(readOnly = true)
    public TenantResponse getTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        // Access tier (lazy-loaded) — within transaction, so Hibernate session is open
        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse assignTier(UUID tenantId, TierAssignRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        QuotaTier newTier = quotaTierRepository.findById(request.tierId())
                .orElseThrow(() -> new TierNotFoundException(request.tierId()));

        if (!newTier.isActive()) {
            throw new IllegalArgumentException("Cannot assign tenant to inactive tier: " + newTier.getName());
        }

        tenant.setTier(newTier);
        tenant = tenantRepository.save(tenant);

        tenantCacheService.invalidate(tenant.getApiKeyHash());

        log.info("Tenant {} reassigned to tier {}", tenantId, newTier.getName());
        return TenantResponse.from(tenant);
    }


    @Transactional
    public TenantResponse suspendTenant(UUID tenantId, String adminUsername) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (tenant.isSuspended()) {
            throw new IllegalStateException("Tenant " + tenantId + " is already suspended");
        }

        tenant.suspend(adminUsername);
        tenant = tenantRepository.save(tenant);

        tenantCacheService.invalidate(tenant.getApiKeyHash());
        log.warn("Tenant {} suspended by {}", tenantId, adminUsername);

        return TenantResponse.from(tenant);
    }

    @Transactional
    public TenantResponse reactivateTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (!tenant.isSuspended()) {
            throw new IllegalStateException("Tenant " + tenantId + " is not suspended");
        }

        tenant.reactivate();
        tenant = tenantRepository.save(tenant);

        tenantCacheService.put(tenant.getApiKeyHash(), TenantConfigCache.from(tenant));

        log.info("Tenant {} reactivated", tenantId);
        return TenantResponse.from(tenant);
    }


    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        // Example output: "xK9mP2qR7vL4bN8jH3aW5eY1fD6cM0tZ"
    }

    public String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
            // Example output: "a3f2b9c1d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1"
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}