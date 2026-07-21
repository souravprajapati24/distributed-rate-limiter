package com.ratelimiter.service;

import com.ratelimiter.domain.entity.QuotaTier;
import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.domain.enums.FailStrategyType;
import com.ratelimiter.domain.enums.LimitEnforcementType;
import com.ratelimiter.domain.enums.TenantStatus;
import com.ratelimiter.dto.internal.AuditEvent;
import com.ratelimiter.repository.QuotaTierRepository;
import com.ratelimiter.repository.RateLimitAuditLogRepository;
import com.ratelimiter.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class AuditConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ratelimiter_test")
            .withUsername("ratelimiter")
            .withPassword("secret");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, AuditEvent> kafkaTemplate;

    @Autowired
    private RateLimitAuditLogRepository auditLogRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private QuotaTierRepository quotaTierRepository;

    @Test
    void publishedAuditEventIsPersistedToPostgresByAuditConsumer() {

        QuotaTier tier = quotaTierRepository.save(QuotaTier.builder()
                .name("AUDIT-CONSUMER-TEST-" + System.nanoTime())
                .algorithm(AlgorithmType.FIXED_WINDOW)
                .requestsPerWindow(10)
                .windowSizeSeconds(60)
                .burstMultiplier(BigDecimal.ONE)
                .limitType(LimitEnforcementType.HARD)
                .active(true)
                .build());

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Audit Consumer Test Tenant")
                .email("audit-consumer-" + System.nanoTime() + "@test.com")
                .apiKeyHash("dummy-hash-" + System.nanoTime())
                .tier(tier)
                .status(TenantStatus.ACTIVE)
                .failStrategy(FailStrategyType.OPEN)
                .build());

        UUID tenantId = tenant.getId(); // real, FK-satisfying ID

        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                tenantId,
                "/api/v1/probe",
                "GET",
                "ALLOWED",
                "FIXED_WINDOW",
                5,
                10,
                5,
                "HARD",
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(30),
                "127.0.0.1",
                Instant.now()
        );

        kafkaTemplate.send("rate-limit-audit", tenantId.toString(), event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var rows = auditLogRepository.findAll();
            assertTrue(rows.stream().anyMatch(r -> r.getTenantId().equals(tenantId)),
                    "Expected a persisted RateLimitAuditLog row for tenant " + tenantId);
        });

        var persisted = auditLogRepository.findAll().stream()
                .filter(r -> r.getTenantId().equals(tenantId))
                .findFirst()
                .orElseThrow();

        assertEquals("ALLOWED", persisted.getDecision().name());
        assertEquals("FIXED_WINDOW", persisted.getAlgorithmUsed());
        assertEquals(5, persisted.getCounterValue());
        assertEquals(10, persisted.getLimitValue());
    }
}