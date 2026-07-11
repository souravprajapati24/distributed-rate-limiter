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
class TokenBucketAlgorithmTest {

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
    private TokenBucketAlgorithm algorithm;

    private TenantConfigCache.TierConfig buildTierConfig(int requestsPerWindow, int windowSeconds, double burstMultiplier) {
        return new TenantConfigCache.TierConfig(
                UUID.randomUUID(), "TOKEN_BUCKET", requestsPerWindow, windowSeconds,
                BigDecimal.valueOf(burstMultiplier), null, "HARD");
    }

    @Test
    void newTenantStartsWithAFullBucketNotAnEmptyOne() {
        TenantConfigCache.TierConfig config = buildTierConfig(10, 60, 2.0);
        String tenantId = UUID.randomUUID().toString();

        RateLimitDecision first = algorithm.evaluate(tenantId, "api:test", config);
        assertTrue(first.allowed(), "A brand-new tenant's first request must be allowed immediately");
    }

    @Test
    void idleTenantCanBurstUpToMaxTokensCapacity() {
        TenantConfigCache.TierConfig config = buildTierConfig(5, 60, 2.0);
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 10; i++) {
            RateLimitDecision d = algorithm.evaluate(tenantId, "api:burst", config);
            assertTrue(d.allowed(), "Request " + (i + 1) + " of 10 should be allowed (within capacity)");
        }

        RateLimitDecision eleventh = algorithm.evaluate(tenantId, "api:burst", config);
        assertFalse(eleventh.allowed(), "The 11th request should exceed the 10-token capacity");
    }

    @Test
    void tokensRefillOverTimeAtTheConfiguredRate() throws InterruptedException {
        TenantConfigCache.TierConfig config = buildTierConfig(10, 1, 1.0);
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 10; i++) {
            algorithm.evaluate(tenantId, "api:refill", config);
        }
        assertFalse(algorithm.evaluate(tenantId, "api:refill", config).allowed());

        Thread.sleep(200);

        RateLimitDecision afterWait = algorithm.evaluate(tenantId, "api:refill", config);
        assertTrue(afterWait.allowed(), "At least one token should have refilled after 200ms at 10 tokens/sec");
    }

    @Test
    void shouldEnforceCorrectlyUnder100ConcurrentThreads() throws InterruptedException {

        TenantConfigCache.TierConfig config = buildTierConfig(100, 3600, 1.0);
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

        assertEquals(100, allowed.get(),
                "Expected exactly 100 allowed (capacity), got " + allowed.get());
    }
}