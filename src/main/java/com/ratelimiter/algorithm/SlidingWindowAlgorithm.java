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

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl:sw:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> slidingWindowScript;

    public SlidingWindowAlgorithm(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/sliding_window.lua")));
        this.slidingWindowScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision evaluate(String tenantId, String endpoint, TenantConfigCache.TierConfig config) {
        long windowSizeSeconds = config.windowSizeSeconds();
        long windowMs = windowSizeSeconds * 1000L;
        long nowMs = Instant.now().toEpochMilli();

        String key = buildKey(tenantId, endpoint);

        List<Long> result = (List<Long>) redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(config.requestsPerWindow()),
                String.valueOf(windowMs),
                String.valueOf(nowMs)
        );

        if (result == null || result.size() < 4) {
            log.error("Sliding window Lua script returned malformed response for key {}: {}", key, result);
            throw new IllegalStateException("Sliding window rate limit script returned an unexpected response");
        }

        boolean allowed   = result.get(0) == 1L;
        int     limit     = result.get(2).intValue();
        int     remaining = result.get(3).intValue();

        long resetAt = Instant.now().getEpochSecond() + windowSizeSeconds;

        log.debug("Sliding window evaluate: tenant={} endpoint={} key={} allowed={} remaining={}/{}",
                tenantId, endpoint, key, allowed, remaining, limit);

        return new RateLimitDecision(allowed, remaining, limit, resetAt, AlgorithmType.SLIDING_WINDOW.name());
    }

    private String buildKey(String tenantId, String endpoint) {
        return KEY_PREFIX + tenantId + ":" + endpoint;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.SLIDING_WINDOW;
    }
}