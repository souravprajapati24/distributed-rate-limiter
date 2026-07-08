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
public class FixedWindowAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl:fw:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> fixedWindowScript;

    public FixedWindowAlgorithm(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/fixed_window.lua")));
        this.fixedWindowScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision evaluate(String tenantId, String endpoint, TenantConfigCache.TierConfig config) {
        long windowSizeSeconds = config.windowSizeSeconds();
        long nowEpochSeconds = Instant.now().getEpochSecond();

        long windowStart = (nowEpochSeconds / windowSizeSeconds) * windowSizeSeconds;
        long windowEnd = windowStart + windowSizeSeconds;

        String key = buildKey(tenantId, endpoint, windowStart);

        List<Long> result = (List<Long>) redisTemplate.execute(
                fixedWindowScript,
                List.of(key),
                String.valueOf(config.requestsPerWindow()),
                String.valueOf(windowSizeSeconds),
                String.valueOf(windowEnd)
        );

        if (result == null || result.size() < 4) {
            log.error("Fixed window Lua script returned malformed response for key {}: {}", key, result);
            throw new IllegalStateException("Fixed window rate limit script returned an unexpected response");
        }

        boolean allowed   = result.get(0) == 1L;
        int     limit     = result.get(2).intValue();
        int     remaining = result.get(3).intValue();
        long    resetAt   = windowEnd;

        log.debug("Fixed window evaluate: tenant={} endpoint={} key={} allowed={} remaining={}/{}",
                tenantId, endpoint, key, allowed, remaining, limit);

        return new RateLimitDecision(allowed, remaining, limit, resetAt, AlgorithmType.FIXED_WINDOW.name());
    }

    private String buildKey(String tenantId, String endpoint, long windowStart) {
        return KEY_PREFIX + tenantId + ":" + endpoint + ":" + windowStart;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.FIXED_WINDOW;
    }
}