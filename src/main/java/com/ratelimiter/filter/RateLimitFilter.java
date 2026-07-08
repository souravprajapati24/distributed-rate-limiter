package com.ratelimiter.filter;

import com.ratelimiter.algorithm.AlgorithmSelector;
import com.ratelimiter.algorithm.RateLimitAlgorithm;
import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.dto.internal.RateLimitDecision;
import com.ratelimiter.dto.internal.TenantConfigCache;
import com.ratelimiter.service.TenantCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final TenantCacheService tenantCacheService;
    private final AlgorithmSelector algorithmSelector;
    private final MeterRegistry meterRegistry;

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
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
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

        if ("SUSPENDED".equals(tenant.status())) {
            writeErrorResponse(response, HttpStatus.FORBIDDEN, "TENANT_SUSPENDED",
                    "Your account has been suspended. Please contact support.");
            return;
        }

        String normalizedEndpoint = normalizeEndpoint(request.getRequestURI());
        AlgorithmType algorithmType = AlgorithmType.valueOf(tenant.tier().algorithm());
        RateLimitAlgorithm algorithm = algorithmSelector.select(algorithmType);

        RateLimitDecision decision = algorithm.evaluate(
                tenant.tenantId().toString(), normalizedEndpoint, tenant.tier());

        response.setHeader("X-RateLimit-Limit",     String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset",     String.valueOf(decision.resetAtEpochSecond()));
        response.setHeader("X-RateLimit-Algorithm", decision.algorithm());

        if (decision.allowed()) {
            meterRegistry.counter("ratelimit.requests.allowed", "algorithm", decision.algorithm()).increment();
            chain.doFilter(request, response);
        } else {
            meterRegistry.counter("ratelimit.requests.denied", "algorithm", decision.algorithm()).increment();
            writeRateLimitExceededResponse(response, decision);
        }
    }

    private void writeRateLimitExceededResponse(HttpServletResponse response, RateLimitDecision decision)
            throws IOException {
        long retryAfterSeconds = Math.max(0, decision.resetAtEpochSecond() - Instant.now().getEpochSecond());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Rate limit exceeded. Retry after %d seconds.\",\"retryAfter\":%d}",
                retryAfterSeconds, retryAfterSeconds
        ));
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

    private String normalizeEndpoint(String uri) {
        return uri.split("\\?")[0]
                .replaceAll("^/|/$", "")
                .replace("/", ":")
                .toLowerCase();
    }
}