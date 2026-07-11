package com.ratelimiter.algorithm;

import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.dto.internal.RateLimitDecision;
import com.ratelimiter.dto.internal.TenantConfigCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class LeakyBucketAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl:lb:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> leakyBucketScript;

    public LeakyBucketAlgorithm(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.leakyBucketScript = new DefaultRedisScript<>();
        this.leakyBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/leaky_bucket.lua")));
        this.leakyBucketScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision evaluate(String tenantId, String endpoint, TenantConfigCache.TierConfig config) {
        BigDecimal leakRatePerSecond = config.leakRatePerSecond();
        if (leakRatePerSecond == null) {
            log.error("Tier configured for LEAKY_BUCKET but leakRatePerSecond is null. tenant={} endpoint={}",
                    tenantId, endpoint);
            throw new IllegalStateException(
                    "LEAKY_BUCKET tier is missing leakRatePerSecond — cannot evaluate rate limit");
        }

        double capacity = config.requestsPerWindow();
        double leakRate = leakRatePerSecond.doubleValue();
        long nowMs = Instant.now().toEpochMilli();

        String key = buildKey(tenantId, endpoint);

        List<Long> result = (List<Long>) redisTemplate.execute(
                leakyBucketScript,
                List.of(key),
                String.valueOf(capacity),
                String.valueOf(leakRate),
                String.valueOf(nowMs)
        );

        if (result == null || result.size() < 4) {
            log.error("Leaky bucket Lua script returned malformed response for key {}: {}", key, result);
            throw new IllegalStateException("Leaky bucket rate limit script returned an unexpected response");
        }

        boolean allowed       = result.get(0) == 1L;
        int     queueSize     = result.get(1).intValue();
        int     bucketCapacity = result.get(2).intValue();
        int     remaining     = result.get(3).intValue();

        long secondsToFreeSlot = leakRate > 0
                ? (long) Math.ceil(1.0 / leakRate)
                : Long.MAX_VALUE;
        long resetAt = Instant.now().getEpochSecond() + Math.max(0, secondsToFreeSlot);

        log.debug("Leaky bucket evaluate: tenant={} endpoint={} key={} allowed={} queue={}/{}",
                tenantId, endpoint, key, allowed, queueSize, bucketCapacity);

        return new RateLimitDecision(allowed, remaining, bucketCapacity, resetAt, AlgorithmType.LEAKY_BUCKET.name());
    }

    private String buildKey(String tenantId, String endpoint) {
        return KEY_PREFIX + tenantId + ":" + endpoint;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.LEAKY_BUCKET;
    }
}