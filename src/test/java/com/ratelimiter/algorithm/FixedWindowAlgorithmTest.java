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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class FixedWindowAlgorithmTest {

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
    private FixedWindowAlgorithm algorithm;

    private TenantConfigCache.TierConfig buildTierConfig(int requestsPerWindow, int windowSizeSeconds) {
        return new TenantConfigCache.TierConfig(
                UUID.randomUUID(),
                "FIXED_WINDOW",
                requestsPerWindow,
                windowSizeSeconds,
                BigDecimal.ONE,
                null,
                "HARD"
        );
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
    void shouldReturnCorrectRemainingAndResetFields() {
        TenantConfigCache.TierConfig config = buildTierConfig(3, 60);
        String tenantId = UUID.randomUUID().toString();

        RateLimitDecision first = algorithm.evaluate(tenantId, "api:remaining-check", config);
        assertTrue(first.allowed());
        assertEquals(2, first.remaining());
        assertEquals(3, first.limit());
        assertEquals("FIXED_WINDOW", first.algorithm());
        assertTrue(first.resetAtEpochSecond() > System.currentTimeMillis() / 1000);
    }

    @Test
    void shouldIsolateDifferentTenantsAndEndpoints() {
        TenantConfigCache.TierConfig config = buildTierConfig(2, 60);
        String tenantA = UUID.randomUUID().toString();
        String tenantB = UUID.randomUUID().toString();

        algorithm.evaluate(tenantA, "api:shared", config);
        algorithm.evaluate(tenantA, "api:shared", config);
        RateLimitDecision tenantADenied = algorithm.evaluate(tenantA, "api:shared", config);
        assertFalse(tenantADenied.allowed(), "Tenant A should be exhausted");


        RateLimitDecision tenantBAllowed = algorithm.evaluate(tenantB, "api:shared", config);
        assertTrue(tenantBAllowed.allowed(), "Tenant B must be isolated from Tenant A's counter");

        RateLimitDecision tenantADifferentEndpoint =
                algorithm.evaluate(tenantA, "api:different", config);
        assertTrue(tenantADifferentEndpoint.allowed(),
                "Tenant A's counter on a different endpoint must be isolated");
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
                try {
                    RateLimitDecision d = algorithm.evaluate(tenantId, "api:concurrent", config);
                    if (d.allowed()) {
                        allowed.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(completed, "All 150 concurrent requests should complete within 10 seconds");
        assertEquals(limit, allowed.get(),
                "Expected exactly " + limit + " allowed requests with zero over-admission, got " + allowed.get());
    }
}