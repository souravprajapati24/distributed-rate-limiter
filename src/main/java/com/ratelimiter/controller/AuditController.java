package com.ratelimiter.controller;

import com.ratelimiter.domain.entity.RateLimitAuditLog;
import com.ratelimiter.domain.enums.DecisionType;
import com.ratelimiter.dto.response.AuditLogResponse;
import com.ratelimiter.exception.TenantNotFoundException;
import com.ratelimiter.repository.RateLimitAuditLogRepository;
import com.ratelimiter.repository.TenantRepository;
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
public class AuditController {

    private final RateLimitAuditLogRepository auditLogRepository;
    private final TenantRepository tenantRepository;

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