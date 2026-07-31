package com.ratelimiter.dto.response;

import java.time.Instant;

public record AuthResponse(
        String  accessToken,
        String  tokenType,
        Instant expiresAt,
        String  username,
        String  role
) {}