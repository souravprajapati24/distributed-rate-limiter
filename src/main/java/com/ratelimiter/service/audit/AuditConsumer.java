package com.ratelimiter.service.audit;

import com.ratelimiter.dto.internal.AuditEvent;
import com.ratelimiter.repository.RateLimitAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Consumes AuditEvent messages from Kafka and persists each one as a
 * RateLimitAuditLog row.
 *
 * MANUAL ACKNOWLEDGMENT CONTRACT (critical):
 * ack.acknowledge() is called ONLY after the database write succeeds. If the
 * repository save() throws, this method lets the exception propagate — it does
 * NOT catch-log-and-acknowledge-anyway. Catching the exception here and still
 * acknowledging would permanently lose the audit event: once an offset is
 * acknowledged under manual ack-mode (application.yml, Phase 1), Kafka will
 * never redeliver that message to this consumer group again, regardless of
 * whether the corresponding database write actually happened.
 *
 * By letting the exception propagate, Spring Kafka's container-level retry
 * mechanism (spring.kafka.consumer/listener config, Phase 1) retries the
 * message up to 3 times. If all 3 retries fail, Spring Kafka automatically
 * republishes the message to rate-limit-audit-dlt and DOES advance the
 * original topic's offset at that point (the message has been "handled" by
 * being moved to the DLT, even though it was never persisted to PostgreSQL).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditConsumer {

    private final RateLimitAuditLogRepository auditLogRepository;
    private final MeterRegistry meterRegistry;
    private final AuditPersistenceService auditPersistenceService;


    @KafkaListener(
            topics = "${rate-limiter.kafka.topics.audit}",
            groupId = "${rate-limiter.kafka.consumer-groups.audit}"
    )
    public void consume(ConsumerRecord<String, AuditEvent> record, Acknowledgment ack) {
        AuditEvent event = record.value();
        try {
            auditPersistenceService.persist(event);

            ack.acknowledge();

            meterRegistry.counter("ratelimit.audit.persisted", "decision", event.decision()).increment();
            log.debug("Persisted audit event {} for tenant {} (partition={}, offset={})",
                    event.eventId(), event.tenantId(), record.partition(), record.offset());
        } catch (Exception e) {

            log.error("Failed to persist audit event {} from tenant {} (partition={}, offset={}). " +
                            "Will retry, then route to DLT if retries are exhausted. Error: {}",
                    event.eventId(), event.tenantId(), record.partition(), record.offset(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${rate-limiter.kafka.topics.audit-dlt}",
            groupId = "${rate-limiter.kafka.consumer-groups.dlt}"
    )
    public void consumeDlt(ConsumerRecord<String, AuditEvent> record, Acknowledgment ack) {
        AuditEvent event = record.value();

        log.error("AUDIT DLT: unrecoverable failure persisting audit event {} for tenant {} " +
                        "(original partition={}, offset={}). This event has NOT been persisted to " +
                        "rate_limit_audit_log and requires manual investigation/replay.",
                event.eventId(), event.tenantId(), record.partition(), record.offset());

        meterRegistry.counter("ratelimit.audit.dlt", "decision", event.decision()).increment();

        ack.acknowledge();
    }



}