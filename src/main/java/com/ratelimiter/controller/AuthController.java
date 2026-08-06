package com.ratelimiter.controller;

import com.ratelimiter.domain.entity.AdminUser;
import com.ratelimiter.dto.request.AuthLoginRequest;
import com.ratelimiter.dto.request.AuthRegisterRequest;
import com.ratelimiter.dto.response.AuthResponse;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.service.AdminUserService;
import com.ratelimiter.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(
        name = "Authentication",
        description = "User login and admin user registration"
)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AdminUserService adminUserService;
    private final JwtService jwtService;

    @Operation(
            summary = "Authenticate admin user and obtain JWT token",
            description = "Authenticates an admin user using username and password." +
                    " Returns a JWT access token that must be supplied in the Authorization header for subsequent protected API requests."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful; JWT token returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error (missing/invalid fields)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials (wrong username or password)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            )
    })

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthLoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );

            AdminUser user = (AdminUser) auth.getPrincipal();
            String token = jwtService.generateToken(user);

            try {
                adminUserService.recordLogin(user.getId());
            } catch (Exception e) {
                log.warn("Failed to update last_login_at for {}: {}", user.getUsername(), e.getMessage());
            }

            Instant expiresAt = Instant.now().plusMillis(jwtService.getExpirationMs());

            log.info("Admin user '{}' logged in", user.getUsername());

            return ResponseEntity.ok(new AuthResponse(
                    token,
                    "Bearer",
                    expiresAt,
                    user.getUsername(),
                    user.getRole().name()
            ));

        } catch (AuthenticationException e) {
            // Do NOT log the attempted username to avoid leaking user enumeration
            log.warn("Failed login attempt (invalid credentials)");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new com.ratelimiter.exception.GlobalExceptionHandler.ErrorResponse(
                            "INVALID_CREDENTIALS", "Username or password is incorrect"));
        }
    }

    @Operation(
            summary = "Register a new admin user",
            description = "Creates a new administrator account with the specified role. " +
                    "**Requires ADMIN privileges.** This endpoint can only be invoked by authenticated administrators.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Admin user created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = java.util.Map.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error or invalid argument",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication required (missing or invalid JWT token)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Insufficient permissions (ADMIN role required)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict (duplicate admin username/email)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRegisterRequest request) {
        AdminUser created = adminUserService.registerAdmin(request);
        log.info("New admin user '{}' created by {}", created.getUsername(),
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication().getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                java.util.Map.of(
                        "id", created.getId(),
                        "username", created.getUsername(),
                        "email", created.getEmail(),
                        "role", created.getRole().name(),
                        "createdAt", created.getCreatedAt()
                )
        );
    }
}