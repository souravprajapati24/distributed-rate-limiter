package com.ratelimiter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.dto.internal.TenantConfigCache;
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

/**
 * Manages the tenant configuration cache in Redis.
 *
 * Cache pattern:
 *   Key:   tenant:config:{apiKeyHash}
 *   Value: JSON-serialized TenantConfigCache
 *   TTL:   30 seconds (rate-limiter.tenant-cache-ttl-seconds)
 *
 * Resolution order:
 *   1. Redis cache (sub-millisecond)
 *   2. PostgreSQL fallback (1-5ms)
 *   3. Empty (unknown API key → 404)
 *
 * Invalidation:
 *   - Called immediately on tier change or tenant suspension
 *   - Allows hot-reload: tier config changes reflect within 5 seconds
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCacheService {

    private static final String KEY_PREFIX = "tenant:config:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${rate-limiter.tenant-cache-ttl-seconds:30}")
    private int cacheTtlSeconds;

    /**
     * Resolve tenant config from cache (Redis) or fallback (PostgreSQL).
     *
     * @param apiKeyHash SHA-256 hash of the API key from the X-Api-Key header
     * @return TenantConfigCache if found, empty Optional if unknown API key
     */
    public Optional<TenantConfigCache> resolve(String apiKeyHash) {
        String cacheKey = KEY_PREFIX + apiKeyHash;

        // Step 1: Try Redis cache
        try {
            String cachedJson = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                meterRegistry.counter("ratelimit.cache.hit").increment();
                log.debug("Cache hit for key suffix ...{}", apiKeyHash.substring(apiKeyHash.length() - 8));
                return Optional.of(objectMapper.readValue(cachedJson, TenantConfigCache.class));
            }
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            // Redis is down — fall through to PostgreSQL
            // The filter will handle Redis unavailability at the algorithm level
            log.warn("Redis unavailable during cache lookup. Falling back to DB. Error: {}", e.getMessage());
        } catch (JsonProcessingException e) {
            // Corrupt cache entry — evict it and reload from DB
            log.error("Failed to deserialize cached tenant config. Evicting corrupt entry.", e);
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception deleteEx) {
                log.warn("Failed to delete corrupt cache entry", deleteEx);
            }
        }

        // Step 2: Fall back to PostgreSQL
        meterRegistry.counter("ratelimit.cache.miss").increment();
        return tenantRepository.findByApiKeyHashWithTier(apiKeyHash)
                .map(tenant -> {
                    TenantConfigCache config = TenantConfigCache.from(tenant);
                    // Populate cache for subsequent requests
                    put(apiKeyHash, config);
                    return config;
                });
    }

    /**
     * Store a tenant config in Redis with TTL.
     * Called after DB fallback (resolve()) and at tenant creation (TenantService).
     */
    public void put(String apiKeyHash, TenantConfigCache config) {
        String cacheKey = KEY_PREFIX + apiKeyHash;
        try {
            String json = objectMapper.writeValueAsString(config);
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(cacheTtlSeconds));
            log.debug("Cached tenant config for key suffix ...{}", apiKeyHash.substring(apiKeyHash.length() - 8));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tenant config for caching. Tenant: {}", config.tenantId(), e);
            // Non-fatal: the next request will retry the DB fallback
        } catch (RedisConnectionFailureException e) {
            log.warn("Failed to write tenant config to cache (Redis unavailable). Tenant: {}", config.tenantId());
        }
    }

    /**
     * Evict a tenant's cache entry.
     *
     * Must be called:
     * 1. Immediately on tenant suspension (so 403 takes effect without waiting for TTL)
     * 2. On tier config change (so new limits take effect immediately)
     * 3. On tenant tier reassignment
     */
    public void invalidate(String apiKeyHash) {
        String cacheKey = KEY_PREFIX + apiKeyHash;
        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            log.debug("Cache invalidated for key suffix ...{}: {}", apiKeyHash.substring(apiKeyHash.length() - 8), deleted);
        } catch (RedisConnectionFailureException e) {
            log.warn("Failed to invalidate cache (Redis unavailable). TTL expiry will handle it. Key suffix: ...{}",
                    apiKeyHash.substring(apiKeyHash.length() - 8));
            // Non-fatal: the 30s TTL will naturally expire the entry
        }
    }

    /**
     * Invalidate cache entries for all tenants on a specific tier.
     * Called by QuotaTierService.update() when tier config changes.
     */
    public void invalidateByTier(List<String> apiKeyHashes) {
        apiKeyHashes.forEach(this::invalidate);
        log.info("Invalidated {} cache entries for tier update", apiKeyHashes.size());
    }
}