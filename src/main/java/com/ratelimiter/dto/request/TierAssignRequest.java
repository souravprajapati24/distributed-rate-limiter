package com.ratelimiter.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TierAssignRequest(

        @NotNull(message = "Tier ID is required")
        UUID tierId

) {}