package com.ratelimiter.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import com.ratelimiter.dto.internal.AuditEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;


@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${rate-limiter.kafka.topics.audit}")
    private String auditTopic;

    @Value("${rate-limiter.kafka.topics.audit-dlt}")
    private String auditDltTopic;


    @Bean
    public NewTopic rateLimitAuditTopic() {
        return TopicBuilder.name(auditTopic)
                .partitions(6)
                .replicas(1)
                .config("retention.ms", String.valueOf(java.time.Duration.ofDays(7).toMillis()))
                .config("cleanup.policy", "delete")
                .build();
    }


    @Bean
    public NewTopic rateLimitAuditDltTopic() {
        return TopicBuilder.name(auditDltTopic)
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(java.time.Duration.ofDays(30).toMillis()))
                .config("cleanup.policy", "delete")
                .build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, AuditEvent> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (consumerRecord, exception) -> {
                    log.error("Routing audit event to DLT after exhausted retries. " +
                                    "Original topic={} partition={} offset={} key={}. Cause: {}",
                            consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(),
                            consumerRecord.key(), exception.getMessage());
                    return new TopicPartition(auditDltTopic, 0);
                }
        );

        FixedBackOff backOff = new FixedBackOff(1_000L, 3L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        return errorHandler;
    }



}