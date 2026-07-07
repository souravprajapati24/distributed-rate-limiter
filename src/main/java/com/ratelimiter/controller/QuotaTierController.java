package com.ratelimiter.controller;

import com.ratelimiter.dto.request.QuotaTierRequest;
import com.ratelimiter.dto.response.QuotaTierResponse;
import com.ratelimiter.service.QuotaTierService;
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
public class QuotaTierController {

    private final QuotaTierService quotaTierService;


    @PostMapping
    public ResponseEntity<QuotaTierResponse> createTier(@Valid @RequestBody QuotaTierRequest request) {
        QuotaTierResponse response = quotaTierService.createTier(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<QuotaTierResponse>> listTiers() {
        return ResponseEntity.ok(quotaTierService.listActiveTiers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuotaTierResponse> getTier(@PathVariable UUID id) {
        return ResponseEntity.ok(quotaTierService.getTier(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuotaTierResponse> updateTier(
            @PathVariable UUID id,
            @Valid @RequestBody QuotaTierRequest request) {
        return ResponseEntity.ok(quotaTierService.updateTier(id, request));
    }
}