package com.ratelimiter.service;

import com.ratelimiter.domain.entity.QuotaTier;
import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.dto.request.QuotaTierRequest;
import com.ratelimiter.dto.response.QuotaTierResponse;
import com.ratelimiter.exception.TierNotFoundException;
import com.ratelimiter.repository.QuotaTierRepository;
import com.ratelimiter.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaTierService {

    private final QuotaTierRepository quotaTierRepository;
    private final TenantRepository tenantRepository;
    private final TenantCacheService tenantCacheService;

    @Transactional
    public QuotaTierResponse createTier(QuotaTierRequest request) {

        if (quotaTierRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Quota tier with name '" + request.name() + "' already exists");
        }

        validateAlgorithmConfig(request);

        QuotaTier tier = QuotaTier.builder()
                .name(request.name())
                .description(request.description())
                .algorithm(request.algorithm())
                .requestsPerWindow(request.requestsPerWindow())
                .windowSizeSeconds(request.windowSizeSeconds())
                .burstMultiplier(request.burstMultiplier() != null ? request.burstMultiplier() : BigDecimal.ONE)
                .leakRatePerSecond(request.leakRatePerSecond())
                .limitType(request.limitType())
                .build();

        tier = quotaTierRepository.save(tier);
        log.info("Created quota tier: {} ({})", tier.getName(), tier.getAlgorithm());
        return QuotaTierResponse.from(tier);
    }

    @Transactional(readOnly = true)
    public List<QuotaTierResponse> listActiveTiers() {
        return quotaTierRepository.findByActiveTrue().stream()
                .map(QuotaTierResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public QuotaTierResponse getTier(UUID tierId) {
        return quotaTierRepository.findById(tierId)
                .map(QuotaTierResponse::from)
                .orElseThrow(() -> new TierNotFoundException(tierId));
    }


    @Transactional
    public QuotaTierResponse updateTier(UUID tierId, QuotaTierRequest request) {
        QuotaTier tier = quotaTierRepository.findById(tierId)
                .orElseThrow(() -> new TierNotFoundException(tierId));

        validateAlgorithmConfig(request);

        tier.setName(request.name());
        tier.setDescription(request.description());
        tier.setAlgorithm(request.algorithm());
        tier.setRequestsPerWindow(request.requestsPerWindow());
        tier.setWindowSizeSeconds(request.windowSizeSeconds());
        tier.setBurstMultiplier(request.burstMultiplier() != null ? request.burstMultiplier() : BigDecimal.ONE);
        tier.setLeakRatePerSecond(request.leakRatePerSecond());
        tier.setLimitType(request.limitType());

        tier = quotaTierRepository.save(tier);

        List<String> affectedApiKeyHashes = tenantRepository.findActiveByTierId(tierId)
                .stream()
                .map(Tenant::getApiKeyHash)
                .toList();

        tenantCacheService.invalidateByTier(affectedApiKeyHashes);

        log.info("Updated tier {} ({}). Invalidated {} tenant cache entries.",
                tier.getName(), tierId, affectedApiKeyHashes.size());

        return QuotaTierResponse.from(tier);
    }

    private void validateAlgorithmConfig(QuotaTierRequest request) {
        if (request.algorithm() == null) return;

        switch (request.algorithm()) {
            case LEAKY_BUCKET -> {
                if (request.leakRatePerSecond() == null || request.leakRatePerSecond().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "leakRatePerSecond is required and must be > 0 for LEAKY_BUCKET algorithm");
                }
            }
            case TOKEN_BUCKET -> {
                if (request.burstMultiplier() == null || request.burstMultiplier().compareTo(BigDecimal.ONE) < 0) {
                    throw new IllegalArgumentException(
                            "burstMultiplier must be >= 1.00 for TOKEN_BUCKET algorithm");
                }
            }
            default -> {}
        }
    }
}