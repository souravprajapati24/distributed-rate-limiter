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
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private static final String KEY_PREFIX = "rl:tb:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    public TokenBucketAlgorithm(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        this.tokenBucketScript.setResultType(List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitDecision evaluate(String tenantId, String endpoint, TenantConfigCache.TierConfig config) {
        BigDecimal burstMultiplier = config.burstMultiplier() != null
                ? config.burstMultiplier() : BigDecimal.ONE;

        double maxTokens = config.requestsPerWindow() * burstMultiplier.doubleValue();
        double refillRate = (double) config.requestsPerWindow() / config.windowSizeSeconds();
        long nowMs = Instant.now().toEpochMilli();

        String key = buildKey(tenantId, endpoint);

        List<Long> result = (List<Long>) redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(maxTokens),
                String.valueOf(refillRate),
                String.valueOf(nowMs)
        );

        if (result == null || result.size() < 4) {
            log.error("Token bucket Lua script returned malformed response for key {}: {}", key, result);
            throw new IllegalStateException("Token bucket rate limit script returned an unexpected response");
        }

        boolean allowed        = result.get(0) == 1L;
        int     tokensRemaining = result.get(1).intValue();
        int     capacity        = result.get(2).intValue();
        long    secondsToNextToken = result.get(4);

        long resetAt = (nowMs / 1000) + secondsToNextToken;

        log.debug("Token bucket evaluate: tenant={} endpoint={} key={} allowed={} tokens={}/{}",
                tenantId, endpoint, key, allowed, tokensRemaining, capacity);

        return new RateLimitDecision(allowed, tokensRemaining, capacity, resetAt, AlgorithmType.TOKEN_BUCKET.name());
    }

    private String buildKey(String tenantId, String endpoint) {
        return KEY_PREFIX + tenantId + ":" + endpoint;
    }

    @Override
    public AlgorithmType getType() {
        return AlgorithmType.TOKEN_BUCKET;
    }
}