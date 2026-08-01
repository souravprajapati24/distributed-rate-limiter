package com.ratelimiter.dto.request;

import jakarta.validation.constraints.*;

public record AuthRegisterRequest(
        @NotBlank @Size(min = 3, max = 50, message = "username must be 3-50 characters")
        String username,

        @NotBlank @Email(message = "email must be valid")
        @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, message = "password must be at least 8 characters")
        String password,

        // Optional — defaults to ROLE_OPERATOR in AdminUserService
        @Pattern(regexp = "ROLE_ADMIN|ROLE_OPERATOR", message = "role must be ROLE_ADMIN or ROLE_OPERATOR")
        String role
) {}