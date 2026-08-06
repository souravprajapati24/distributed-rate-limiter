package com.ratelimiter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(
        name = "Testing",
        description = "Endpoints for connectivity and rate-limiter testing"
)
public class TestingController {

    @Operation(
            summary = "Test rate limiter endpoint",
            description = "Simple endpoint used to verify API availability and test rate-limiting behavior." +
                    " Returns a plain text response when the service is reachable."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Service is operational",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Rate limiter is working")
                    )
            ),
    })
    @GetMapping("/test")
    public ResponseEntity<String> probe() {
        return ResponseEntity.ok("Rate limiter is working");
    }
}
