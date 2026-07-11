package com.ratelimiter.algorithm;

import com.ratelimiter.dto.internal.RateLimitDecision;
import com.ratelimiter.dto.internal.TenantConfigCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class SlidingWindowAlgorithmTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ratelimiter_test")
            .withUsername("ratelimiter")
            .withPassword("secret");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private SlidingWindowAlgorithm algorithm;

    private TenantConfigCache.TierConfig buildTierConfig(int limit, int windowSeconds) {
        return new TenantConfigCache.TierConfig(
                UUID.randomUUID(), "SLIDING_WINDOW", limit, windowSeconds,
                BigDecimal.ONE, null, "HARD");
    }

    @Test
    void shouldAllowRequestsUpToLimit() {
        TenantConfigCache.TierConfig config = buildTierConfig(10, 60);
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 10; i++) {
            RateLimitDecision decision = algorithm.evaluate(tenantId, "api:test", config);
            assertTrue(decision.allowed(), "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void shouldDenyRequestsAfterLimit() {
        TenantConfigCache.TierConfig config = buildTierConfig(5, 60);
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            algorithm.evaluate(tenantId, "api:test", config);
        }

        RateLimitDecision denied = algorithm.evaluate(tenantId, "api:test", config);
        assertFalse(denied.allowed());
        assertEquals(0, denied.remaining());
    }

    @Test
    void deniedRequestsAreNotRecordedInTheWindow() {

        TenantConfigCache.TierConfig config = buildTierConfig(3, 60);
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            algorithm.evaluate(tenantId, "api:test", config);
        }

        for (int i = 0; i < 20; i++) {
            RateLimitDecision decision = algorithm.evaluate(tenantId, "api:test", config);
            assertFalse(decision.allowed());
            assertEquals(0, decision.remaining());
        }
    }

    @Test
    void shouldEnforceCorrectlyUnder100ConcurrentThreads() throws InterruptedException {
        int limit = 100;
        TenantConfigCache.TierConfig config = buildTierConfig(limit, 60);
        String tenantId = UUID.randomUUID().toString();

        AtomicInteger allowed = new AtomicInteger(0);
        int totalRequests = 150;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        ExecutorService pool = Executors.newFixedThreadPool(50);

        for (int i = 0; i < totalRequests; i++) {
            pool.submit(() -> {
                RateLimitDecision d = algorithm.evaluate(tenantId, "api:concurrent", config);
                if (d.allowed()) allowed.incrementAndGet();
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(limit, allowed.get(),
                "Expected exactly " + limit + " allowed, got " + allowed.get());
    }

    @Test
    void shouldIsolateDifferentTenantsAndEndpoints() {
        TenantConfigCache.TierConfig config = buildTierConfig(5, 60);
        String tenantA = UUID.randomUUID().toString();
        String tenantB = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            assertTrue(algorithm.evaluate(tenantA, "api:test", config).allowed());
        }
        assertFalse(algorithm.evaluate(tenantA, "api:test", config).allowed(),
                "Tenant A should be exhausted");

        assertTrue(algorithm.evaluate(tenantB, "api:test", config).allowed(),
                "Tenant B must have an independent counter from Tenant A");

        assertTrue(algorithm.evaluate(tenantA, "api:other", config).allowed(),
                "A different endpoint for the same tenant must have an independent counter");
    }
}