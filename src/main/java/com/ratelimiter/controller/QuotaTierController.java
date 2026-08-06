package com.ratelimiter.controller;

import com.ratelimiter.dto.request.QuotaTierRequest;
import com.ratelimiter.dto.response.QuotaTierResponse;
import com.ratelimiter.exception.GlobalExceptionHandler;
import com.ratelimiter.service.QuotaTierService;
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
@RequestMapping("/api/v1/tiers")
@RequiredArgsConstructor
@Tag(
        name = "Quota Tiers",
        description = "Rate-limit tier definitions and management"
)
public class QuotaTierController {

    private final QuotaTierService quotaTierService;

    @Operation(
            summary = "Create a new quota tier",
            description = "Creates a rate-limiting tier with specified algorithm and limits. " +
                    "Once created, tier can be assigned to multiple tenants. All parameters are required except description.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Tier created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QuotaTierResponse.class)
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
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            )
    })

    @PostMapping
    public ResponseEntity<QuotaTierResponse> createTier(@Valid @RequestBody QuotaTierRequest request) {
        QuotaTierResponse response = quotaTierService.createTier(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "List all active quota tiers",
            description = "Retrieves all currently active quota tiers in the system. Does not include deactivated tiers.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tiers retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QuotaTierResponse.class)
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
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GlobalExceptionHandler.ErrorResponse.class)
                    )
            )
    })

    @GetMapping
    public ResponseEntity<List<QuotaTierResponse>> listTiers() {
        return ResponseEntity.ok(quotaTierService.listActiveTiers());
    }

    @Operation(
            summary = "Retrieve a specific quota tier by ID",
            description = "Fetches full tier definition including algorithm, limits, and burst configuration.",
            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tier details retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QuotaTierResponse.class)
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
                    description = "Resource not found (tier ID does not exist)",
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
    public ResponseEntity<QuotaTierResponse> getTier(@PathVariable UUID id) {
        return ResponseEntity.ok(quotaTierService.getTier(id));
    }

    @Operation(
            summary = "Update an existing quota tier",
            description = "Modifies tier configuration (limits, window size, burst multiplier). " +
                    "Changes take effect immediately for new tenant requests. Existing limits remain " +
                    "enforced until cache expiry.",

            security = @SecurityRequirement(name = "Bearer Authentication")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tier updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QuotaTierResponse.class)
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
                    description = "Resource not found (tier ID does not exist)",
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

    @PutMapping("/{id}")
    public ResponseEntity<QuotaTierResponse> updateTier(
            @PathVariable UUID id,
            @Valid @RequestBody QuotaTierRequest request) {
        return ResponseEntity.ok(quotaTierService.updateTier(id, request));
    }
}