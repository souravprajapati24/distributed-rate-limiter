package com.ratelimiter.controller;

import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.enums.GranularityType;
import com.ratelimiter.dto.response.UsageHistoryResponse;
import com.ratelimiter.dto.response.UsageResponse;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.exception.TenantNotFoundException;
import com.ratelimiter.repository.TenantRepository;
import com.ratelimiter.repository.UsageSummaryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@Slf4j
@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
@Tag(
        name = "Usage Analytics",
        description = "Real-time and historical usage reporting"
)
public class UsageController {

    private static final String FIXED_WINDOW_PREFIX = "rl:fw:";
    private static final String SLIDING_WINDOW_PREFIX = "rl:sw:";
    private static final String TOKEN_BUCKET_PREFIX = "rl:tb:";
    private static final String LEAKY_BUCKET_PREFIX = "rl:lb:";

    private final TenantRepository tenantRepository;
    private final UsageSummaryRepository usageSummaryRepository;
    private final RedisTemplate<String, Object> redisTemplate;


    @Operation(
            summary = "Get current usage snapshot for a tenant",
            description = "Retrieves real-time usage statistics for the specified tenant, including current usage, remaining quota, reset time, and sampling timestamp. " +
                    "Supports all available rate-limiting algorithms.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usage snapshot retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UsageResponse.class)
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
                    description = "Insufficient permissions (ADMIN or OPERATOR role required)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Resource not found (tenant ID does not exist)",
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
    @GetMapping("/{tenantId}")
    public ResponseEntity<UsageResponse> getCurrentUsage(@PathVariable UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        String algorithm = tenant.getTier().getAlgorithm().name();
        int requestsPerWindow = tenant.getTier().getRequestsPerWindow();
        int windowSizeSeconds = tenant.getTier().getWindowSizeSeconds();

        String tenantIdStr = tenant.getId().toString();
        String endpointPlaceholder = "*";

        UsageResponse response = switch (algorithm) {
            case "FIXED_WINDOW" -> readFixedWindowUsage(tenantIdStr, endpointPlaceholder, requestsPerWindow, windowSizeSeconds);
            case "SLIDING_WINDOW" -> readSlidingWindowUsage(tenantIdStr, endpointPlaceholder, requestsPerWindow, windowSizeSeconds);
            case "TOKEN_BUCKET" -> readTokenBucketUsage(tenant, tenantIdStr, endpointPlaceholder, requestsPerWindow, windowSizeSeconds);
            case "LEAKY_BUCKET" -> readLeakyBucketUsage(tenant, tenantIdStr, endpointPlaceholder, requestsPerWindow);
            default -> throw new IllegalStateException("Unknown algorithm: " + algorithm);
        };

        return ResponseEntity.ok(response);
    }


    @Operation(
            summary = "Get historical usage data with pagination",
            description = "Returns aggregated usage statistics for the specified time period and granularity. " +
                    "Supports hourly and daily aggregation with paginated results sorted by the most recent period first.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Usage history retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
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
                    description = "Insufficient permissions (ADMIN or OPERATOR role required)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Resource not found (tenant ID does not exist)",
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

    @GetMapping("/{tenantId}/history")
    public ResponseEntity<Map<String, Object>> getUsageHistory(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "HOURLY") GranularityType granularity,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException(tenantId);
        }

        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.now();

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodStart"));

        Page<UsageHistoryResponse> historyPage = usageSummaryRepository
                .findByTenantIdAndGranularityAndPeriodStartBetween(
                        tenantId, granularity, effectiveFrom, effectiveTo, pageable)
                .map(UsageHistoryResponse::from);

        return ResponseEntity.ok(Map.of(
                "content", historyPage.getContent(),
                "page", historyPage.getNumber(),
                "size", historyPage.getSize(),
                "totalElements", historyPage.getTotalElements(),
                "totalPages", historyPage.getTotalPages()
        ));
    }


    private UsageResponse readFixedWindowUsage(String tenantId, String endpoint, int limit, int windowSizeSeconds) {
        long windowStart = (Instant.now().getEpochSecond() / windowSizeSeconds) * windowSizeSeconds;
        String key = FIXED_WINDOW_PREFIX + tenantId + ":" + endpoint + ":" + windowStart;

        Object raw = redisTemplate.opsForValue().get(key);
        int used = raw != null ? Integer.parseInt(raw.toString()) : 0;
        int remaining = Math.max(0, limit - used);
        long resetAt = windowStart + windowSizeSeconds;

        return new UsageResponse(UUID.fromString(tenantId), "FIXED_WINDOW", limit, remaining, used, resetAt, Instant.now());
    }

    private UsageResponse readSlidingWindowUsage(String tenantId, String endpoint, int limit, int windowSizeSeconds) {
        String key = SLIDING_WINDOW_PREFIX + tenantId + ":" + endpoint;

        Long used = redisTemplate.opsForZSet().zCard(key);
        int usedCount = used != null ? used.intValue() : 0;
        int remaining = Math.max(0, limit - usedCount);
        long resetAt = Instant.now().getEpochSecond() + windowSizeSeconds;

        return new UsageResponse(UUID.fromString(tenantId), "SLIDING_WINDOW", limit, remaining, usedCount, resetAt, Instant.now());
    }

    private UsageResponse readTokenBucketUsage(Tenant tenant, String tenantId, String endpoint, int requestsPerWindow, int windowSizeSeconds) {
        String key = TOKEN_BUCKET_PREFIX + tenantId + ":" + endpoint;
        double maxTokens = requestsPerWindow * tenant.getTier().getBurstMultiplier().doubleValue();

        Object tokensRaw = redisTemplate.opsForHash().get(key, "tokens");
        double tokens = tokensRaw != null ? Double.parseDouble(tokensRaw.toString()) : maxTokens;
        int remaining = (int) Math.floor(Math.min(tokens, maxTokens));
        int used = (int) Math.max(0, Math.floor(maxTokens) - remaining);
        long resetAt = Instant.now().getEpochSecond() + windowSizeSeconds;

        return new UsageResponse(UUID.fromString(tenantId), "TOKEN_BUCKET", (int) maxTokens, remaining, used, resetAt, Instant.now());
    }

    private UsageResponse readLeakyBucketUsage(Tenant tenant, String tenantId, String endpoint, int capacity) {
        String key = LEAKY_BUCKET_PREFIX + tenantId + ":" + endpoint;

        Object queueSizeRaw = redisTemplate.opsForHash().get(key, "queue_size");
        int queueSize = queueSizeRaw != null ? Integer.parseInt(queueSizeRaw.toString()) : 0;
        int remaining = Math.max(0, capacity - queueSize);
        long resetAt = Instant.now().getEpochSecond() + 60;

        return new UsageResponse(UUID.fromString(tenantId), "LEAKY_BUCKET", capacity, remaining, queueSize, resetAt, Instant.now());
    }
}