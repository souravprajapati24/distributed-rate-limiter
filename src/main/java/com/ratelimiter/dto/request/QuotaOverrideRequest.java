package com.ratelimiter.dto.request;

import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.domain.enums.LimitEnforcementType;
import jakarta.validation.constraints.*;

public record QuotaOverrideRequest(

        @NotBlank(message = "Endpoint pattern is required")
        @Size(max = 255, message = "Endpoint pattern must not exceed 255 characters")
        String endpointPattern,

        @NotNull(message = "Requests per window is required")
        @Min(1) @Max(10_000_000)
        Integer requestsPerWindow,

        @NotNull(message = "Window size is required")
        @Min(1) @Max(86400)
        Integer windowSizeSeconds,

        AlgorithmType algorithm,

        LimitEnforcementType limitType

) {}