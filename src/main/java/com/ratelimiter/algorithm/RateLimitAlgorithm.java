package com.ratelimiter.algorithm;

import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.dto.internal.RateLimitDecision;
import com.ratelimiter.dto.internal.TenantConfigCache;

public interface RateLimitAlgorithm {

    RateLimitDecision evaluate(String tenantId, String endpoint, TenantConfigCache.TierConfig config);

    AlgorithmType getType();
}