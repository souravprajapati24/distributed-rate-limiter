package com.ratelimiter.dto.request;

import jakarta.validation.constraints.Size;

public record KeyRotationRequest(

        @Size(max = 255, message = "rotatedBy must not exceed 255 characters")
        String rotatedBy,

        @Size(max = 1000, message = "reason must not exceed 1000 characters")
        String reason

) {}