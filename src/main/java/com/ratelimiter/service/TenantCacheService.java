package com.ratelimiter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.domain.entity.TenantQuotaOverride;
import com.ratelimiter.dto.internal.TenantConfigCache;
import com.ratelimiter.repository.TenantQuotaOverrideRepository;
import com.ratelimiter.repository.TenantRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCacheService {

    private static final String KEY_PREFIX = "tenant:config:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final TenantRepository tenantRepository;
    private final TenantQuotaOverrideRepository tenantQuotaOverrideRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${rate-limiter.tenant-cache-ttl-seconds:30}")
    private int cacheTtlSeconds;

    public Optional<TenantConfigCache> resolve(String apiKeyHash) {
        String cacheKey = KEY_PREFIX + apiKeyHash;

        try {
            String cachedJson = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                meterRegistry.counter("ratelimit.cache.hit").increment();
                log.debug("Cache hit for key suffix ...{}", apiKeyHash.substring(apiKeyHash.length() - 8));
                return Optional.of(objectMapper.readValue(cachedJson, TenantConfigCache.class));
            }
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("Redis unavailable during cache lookup. Falling back to DB. Error: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached tenant config. Evicting corrupt entry.", e);
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception deleteEx) {
                log.warn("Failed to delete corrupt cache entry", deleteEx);
            }
        }


        meterRegistry.counter("ratelimit.cache.miss").increment();
        return tenantRepository.findByApiKeyHashWithTier(apiKeyHash)
                .map(tenant -> {
                    List<TenantQuotaOverride> overrides = tenantQuotaOverrideRepository.findByTenantIdAndActiveTrue(tenant.getId());
                    TenantConfigCache config = TenantConfigCache.from(tenant , overrides);
                    put(apiKeyHash, config);
                    return config;
                });
    }

    public void put(String apiKeyHash, TenantConfigCache config) {
        String cacheKey = KEY_PREFIX + apiKeyHash;
        try {
            String json = objectMapper.writeValueAsString(config);
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(cacheTtlSeconds));
            log.debug("Cached tenant config for key suffix ...{}", apiKeyHash.substring(apiKeyHash.length() - 8));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tenant config for caching. Tenant: {}", config.tenantId(), e);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("Failed to write tenant config to cache (Redis unavailable). Tenant: {}", config.tenantId());
        }
    }

    public void invalidate(String apiKeyHash) {
        String cacheKey = KEY_PREFIX + apiKeyHash;
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.debug("Cache invalidated for key suffix ...{}: {}", apiKeyHash.substring(apiKeyHash.length() - 8), deleted);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.warn("Failed to invalidate cache (Redis unavailable). TTL expiry will handle it. Key suffix: ...{}",
                    apiKeyHash.substring(apiKeyHash.length() - 8));
        }
    }

    public void invalidateByTier(List<String> apiKeyHashes) {
        apiKeyHashes.forEach(this::invalidate);
        log.info("Invalidated {} cache entries for tier update", apiKeyHashes.size());
    }
}