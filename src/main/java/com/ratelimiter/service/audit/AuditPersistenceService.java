package com.ratelimiter.service.audit;

import com.ratelimiter.domain.entity.RateLimitAuditLog;
import com.ratelimiter.dto.internal.AuditEvent;
import com.ratelimiter.repository.RateLimitAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditPersistenceService {

    private final RateLimitAuditLogRepository auditLogRepository;

    @Transactional
    public void persist(AuditEvent event) {
        auditLogRepository.save(RateLimitAuditLog.from(event));
    }
}
