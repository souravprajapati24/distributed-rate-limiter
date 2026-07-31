package com.ratelimiter.controller;

import com.ratelimiter.domain.entity.AdminUser;
import com.ratelimiter.dto.request.AuthLoginRequest;
import com.ratelimiter.dto.request.AuthRegisterRequest;
import com.ratelimiter.dto.response.AuthResponse;
import com.ratelimiter.service.AdminUserService;
import com.ratelimiter.service.JwtService;
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
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AdminUserService adminUserService;
    private final JwtService jwtService;

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