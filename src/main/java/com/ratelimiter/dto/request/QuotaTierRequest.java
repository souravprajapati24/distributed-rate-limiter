package com.ratelimiter.dto.request;

import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.domain.enums.LimitEnforcementType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record QuotaTierRequest(

        @NotBlank(message = "Tier name is required")
        @Size(max = 100, message = "Tier name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotNull(message = "Algorithm is required")
        AlgorithmType algorithm,

        @NotNull(message = "Requests per window is required")
        @Min(value = 1, message = "Requests per window must be at least 1")
        @Max(value = 10_000_000, message = "Requests per window must not exceed 10,000,000")
        Integer requestsPerWindow,

        @NotNull(message = "Window size is required")
        @Min(value = 1, message = "Window size must be at least 1 second")
        @Max(value = 86400, message = "Window size must not exceed 86400 seconds (1 day)")
        Integer windowSizeSeconds,

        @DecimalMin(value = "1.00", message = "Burst multiplier must be at least 1.00")
        @DecimalMax(value = "100.00", message = "Burst multiplier must not exceed 100.00")
        BigDecimal burstMultiplier,

        @DecimalMin(value = "0.01", message = "Leak rate must be at least 0.01 per second")
        BigDecimal leakRatePerSecond,

        @NotNull(message = "Limit type is required")
        LimitEnforcementType limitType

) {}