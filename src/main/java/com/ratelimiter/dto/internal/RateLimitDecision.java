package com.ratelimiter.dto.internal;
public record RateLimitDecision(
        boolean allowed,
        int     remaining,
        int     limit,
        long    resetAtEpochSecond,
        String  algorithm
) {}