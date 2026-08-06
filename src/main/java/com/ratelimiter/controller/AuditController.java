package com.ratelimiter.controller;

import com.ratelimiter.domain.entity.RateLimitAuditLog;
import com.ratelimiter.domain.enums.DecisionType;
import com.ratelimiter.dto.response.AuditLogResponse;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.exception.TenantNotFoundException;
import com.ratelimiter.repository.RateLimitAuditLogRepository;
import com.ratelimiter.repository.TenantRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(
        name = "Audit Logs",
        description = "Rate-limit decision auditing and analysis"
)
public class AuditController {

    private final RateLimitAuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;

    @Operation(
            summary = "Query audit log with flexible filtering",
            description = "Retrieves rate-limit enforcement audit log entries. Supports filtering by tenant ID, decision, and time range. " +
                    "Returns paginated results ordered from the most recent entries to the oldest. If no filters are provided, all audit log entries are returned.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Audit logs retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Map.class)
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
    @GetMapping
    public ResponseEntity<Map<String, Object>> queryAuditLog(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) DecisionType decision,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (tenantId != null && !tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException(tenantId);
        }

        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.now();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "evaluatedAt"));

        Page<RateLimitAuditLog> results = resolveQuery(tenantId, decision, effectiveFrom, effectiveTo, pageable);
        Page<AuditLogResponse> responsePage = results.map(AuditLogResponse::from);

        log.debug("Audit query: tenantId={} decision={} from={} to={} page={} size={} -> {} results",
                tenantId, decision, effectiveFrom, effectiveTo, page, size, responsePage.getTotalElements());

        return ResponseEntity.ok(Map.of(
                "content", responsePage.getContent(),
                "page", responsePage.getNumber(),
                "size", responsePage.getSize(),
                "totalElements", responsePage.getTotalElements(),
                "totalPages", responsePage.getTotalPages()
        ));
    }


    private Page<RateLimitAuditLog> resolveQuery(UUID tenantId, DecisionType decision,
                                                 Instant from, Instant to, Pageable pageable) {
        if (tenantId != null && decision != null) {
            return auditLogRepository.findByTenantIdAndDecisionAndEvaluatedAtBetween(
                    tenantId, decision, from, to, pageable);
        }
        if (tenantId != null) {
            return auditLogRepository.findByTenantIdAndEvaluatedAtBetween(tenantId, from, to, pageable);
        }
        if (decision != null) {
            return auditLogRepository.findByDecisionAndEvaluatedAtBetween(decision, from, to, pageable);
        }
        return auditLogRepository.findAllByEvaluatedAtBetween(from, to, pageable);
    }
}