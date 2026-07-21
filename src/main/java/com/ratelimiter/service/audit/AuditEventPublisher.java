package com.ratelimiter.service.audit;

import com.ratelimiter.dto.internal.AuditEvent;
import com.ratelimiter.dto.internal.RateLimitDecision;
import com.ratelimiter.dto.internal.TenantConfigCache;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    @Value("${rate-limiter.kafka.topics.audit}")
    private String auditTopic;

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Async("auditExecutor")
    public void publish(TenantConfigCache tenant, RateLimitDecision decision,
                        String effectiveDecision,
                        String effectiveLimitType,
                        HttpServletRequest request) {
        AuditEvent event = AuditEvent.from(tenant, decision, effectiveDecision, effectiveLimitType, request);

        kafkaTemplate.send(auditTopic, tenant.tenantId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Audit publish failed for tenant {} event {} (decision={}): {}",
                                tenant.tenantId(), event.eventId(), effectiveDecision, ex.getMessage(), ex);
                        meterRegistry.counter("ratelimit.audit.failed",
                                "decision", event.decision()).increment();
                    } else {
                        log.debug("Audit event {} published for tenant {} (decision={}) to partition {} offset {}",
                                event.eventId(), tenant.tenantId(), effectiveDecision,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        meterRegistry.counter("ratelimit.audit.published",
                                "decision", event.decision()).increment();
                    }
                });
    }
}