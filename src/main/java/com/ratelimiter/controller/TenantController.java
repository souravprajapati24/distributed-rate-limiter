package com.ratelimiter.controller;

import com.ratelimiter.dto.request.TenantRequest;
import com.ratelimiter.dto.request.TierAssignRequest;
import com.ratelimiter.dto.response.TenantResponse;
import com.ratelimiter.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    public ResponseEntity<TenantResponse> registerTenant(@Valid @RequestBody TenantRequest request) {
        TenantResponse response = tenantService.registerTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.getTenant(id));
    }


    @PatchMapping("/{id}/tier")
    public ResponseEntity<TenantResponse> assignTier(
            @PathVariable UUID id,
            @Valid @RequestBody TierAssignRequest request) {
        return ResponseEntity.ok(tenantService.assignTier(id, request));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<TenantResponse> suspendTenant(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "admin") String suspendedBy) {
        return ResponseEntity.ok(tenantService.suspendTenant(id, suspendedBy));
    }


    @PostMapping("/{id}/reactivate")
    public ResponseEntity<TenantResponse> reactivateTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(tenantService.reactivateTenant(id));
    }
}