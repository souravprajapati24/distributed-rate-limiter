package com.ratelimiter.filter;

import com.ratelimiter.dto.internal.TenantConfigCache;
import com.ratelimiter.service.TenantCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;


@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final TenantCacheService tenantCacheService;


    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator",
            "/api/v1/tiers",
            "/api/v1/tenants",
            "/api/v1/audit",
            "/api/v1/usage"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-Api-Key");

        if (apiKey == null || apiKey.isBlank()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "MISSING_API_KEY",
                    "X-Api-Key header is required");
            return;
        }

        String apiKeyHash = hashApiKey(apiKey);
        Optional<TenantConfigCache> tenantOpt = tenantCacheService.resolve(apiKeyHash);

        if (tenantOpt.isEmpty()) {
            writeErrorResponse(response, HttpStatus.NOT_FOUND, "UNKNOWN_API_KEY",
                    "API key not recognized");
            return;
        }

        TenantConfigCache tenant = tenantOpt.get();

        // Step 3: Check tenant status
        if ("SUSPENDED".equals(tenant.status())) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "TENANT_SUSPENDED",
                    "Your account has been suspended. Please contact support.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status,
                                    String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message)
        );
    }

    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}