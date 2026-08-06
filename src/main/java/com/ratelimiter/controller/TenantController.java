package com.ratelimiter.controller;

import com.ratelimiter.dto.request.KeyRotationRequest;
import com.ratelimiter.dto.request.QuotaOverrideRequest;
import com.ratelimiter.dto.request.TenantRequest;
import com.ratelimiter.dto.request.TierAssignRequest;
import com.ratelimiter.dto.response.KeyRotationResponse;
import com.ratelimiter.dto.response.TenantQuotaOverrideResponse;
import com.ratelimiter.dto.response.TenantResponse;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(
        name = "Tenants",
        description = "Tenant lifecycle management (CRUD, suspension, key rotation, overrides)"
)
public class TenantController {

    private final TenantService tenantService;


    @Operation(
            summary = "Register a new tenant",
            description = "Creates a new tenant with a name, email, and assigned quota tier. " +
                    "Generates a unique tenant ID and initial API key. Tenant status defaults to ACTIVE.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tenant created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class)
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
                    description = "Conflict (duplicate tenant email)",
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
    @PostMapping
    public ResponseEntity<TenantResponse> registerTenant(@Valid @RequestBody TenantRequest request) {
        TenantResponse response = tenantService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Retrieve tenant details by ID",
            description = "Fetches full tenant record including assigned tier, status, and metadata.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tenant details retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class)
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

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getTenant(id));
    }

    @Operation(
            summary = "Assign a quota tier to a tenant",
            description = "Updates the tenant's assigned quota tier. Takes effect immediately for new requests. " +
                    "Existing cached limits are invalidated.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tier assigned successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class)
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
                    responseCode = "404",
                    description = "Resource not found (tenant or tier ID does not exist)",
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
    @PatchMapping("/{id}/tier")
    public ResponseEntity<TenantResponse> assignTier(
            @PathVariable UUID id,
            @Valid @RequestBody TierAssignRequest request) {
        return ResponseEntity.ok(tenantService.assignTier(id, request));
    }

    @Operation(
            summary = "Suspend tenant's API access",
            description = "Immediately suspends a tenant's access to the rate-limiting service. " +
                    "All subsequent requests from this tenant are denied.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tenant suspended successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class)
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
    @PostMapping("/{id}/suspend")
    public ResponseEntity<TenantResponse> suspendTenant(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "admin") String suspendedBy) {
        return ResponseEntity.ok(tenantService.suspendTenant(id, suspendedBy));
    }


    @Operation(
            summary = "Reactivate suspended tenant",
            description = "Restores a suspended tenant to ACTIVE status. Access is immediately restored. " +
                    "Timestamp of reactivation is recorded.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tenant reactivated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantResponse.class)
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
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<TenantResponse> reactivateTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.reactivateTenant(id));
    }


    @Operation(
            summary = "Create endpoint-specific quota override",
            description = "Allows tenant-level quota overrides for specific endpoint patterns. " +
                    "Overrides take precedence over tier-level defaults. Useful for protecting critical " +
                    "or high-traffic endpoints.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Override created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantQuotaOverrideResponse.class)
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
    @PostMapping("/{id}/overrides")
    public ResponseEntity<TenantQuotaOverrideResponse> createOverride(
            @PathVariable UUID id,
            @Valid @RequestBody QuotaOverrideRequest request) {
        TenantQuotaOverrideResponse response = tenantService.createOverride(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List active quota overrides for tenant",
            description = "Retrieves all active endpoint-specific quota overrides for a given tenant. " +
                    "Returns empty list if no overrides exist.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Overrides retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantQuotaOverrideResponse.class)
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

    @GetMapping("/{id}/overrides")
    public ResponseEntity<List<TenantQuotaOverrideResponse>> listOverrides(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.listOverrides(id));
    }


    @Operation(
            summary = "Deactivate (soft-delete) quota override",
            description = "Deactivates a specific endpoint override without deleting historical records. " +
                    "Tier-level defaults resume for the endpoint pattern.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Override deactivated successfully (no content)"
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
                    responseCode = "404",
                    description = "Resource not found (tenant or override ID does not exist)",
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
    @DeleteMapping("/{id}/overrides/{overrideId}")
    public ResponseEntity<Void> deactivateOverride(
            @PathVariable UUID id,
            @PathVariable UUID overrideId) {
        tenantService.deactivateOverride(id, overrideId);
        return ResponseEntity.noContent().build();
    }


    @Operation(
            summary = "Rotate tenant API key",
            description = "Generates a new API key for the tenant and invalidates the old one. " +
                    "Includes audit trail with rotatedBy and reason. New key is shown only in response; " +
                    "old key hash prefix shown for audit.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "API key rotated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KeyRotationResponse.class)
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
    @PostMapping("/{id}/rotate-key")
    public ResponseEntity<KeyRotationResponse> rotateApiKey(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) KeyRotationRequest request) {
        KeyRotationResponse response = tenantService.rotateApiKey(id, request);
        return ResponseEntity.ok(response);
    }
}