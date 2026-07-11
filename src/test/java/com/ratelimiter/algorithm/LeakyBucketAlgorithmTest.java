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
class LeakyBucketAlgorithmTest {
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
    private LeakyBucketAlgorithm algorithm;

    private TenantConfigCache.TierConfig buildTierConfig(int capacity, double leakRatePerSecond) {
        return new TenantConfigCache.TierConfig(
                UUID.randomUUID(), "LEAKY_BUCKET", capacity, 60,
                BigDecimal.ONE, BigDecimal.valueOf(leakRatePerSecond), "HARD");
    }

    @Test
    void newTenantStartsWithAnEmptyQueue() {
        TenantConfigCache.TierConfig config = buildTierConfig(5, 1.0);
        String tenantId = UUID.randomUUID().toString();

        RateLimitDecision first = algorithm.evaluate(tenantId, "api:test", config);
        assertTrue(first.allowed());
        assertEquals(4, first.remaining());
    }

    @Test
    void shouldDenyWhenQueueIsFull() {
        TenantConfigCache.TierConfig config = buildTierConfig(5, 0.001); // negligible drain
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 5; i++) {
            assertTrue(algorithm.evaluate(tenantId, "api:test", config).allowed());
        }

        RateLimitDecision sixth = algorithm.evaluate(tenantId, "api:test", config);
        assertFalse(sixth.allowed(), "6th request should overflow a 5-capacity queue");
    }

    @Test
    void queueDrainsOverTimeAtTheConfiguredLeakRate() throws InterruptedException {
        // capacity=3, leakRate=10/sec -> a single slot frees up in ~100ms
        TenantConfigCache.TierConfig config = buildTierConfig(3, 10.0);
        String tenantId = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            algorithm.evaluate(tenantId, "api:drain", config);
        }
        assertFalse(algorithm.evaluate(tenantId, "api:drain", config).allowed());

        Thread.sleep(200);

        RateLimitDecision afterDrain = algorithm.evaluate(tenantId, "api:drain", config);
        assertTrue(afterDrain.allowed(), "Queue should have drained by at least one slot after 200ms");
    }

    @Test
    void missingLeakRateThrowsRatherThanSilentlyDefaulting() {
        TenantConfigCache.TierConfig config = new TenantConfigCache.TierConfig(
                UUID.randomUUID(), "LEAKY_BUCKET", 10, 60, BigDecimal.ONE, null, "HARD");

        assertThrows(IllegalStateException.class,
                () -> algorithm.evaluate(UUID.randomUUID().toString(), "api:test", config),
                "A LEAKY_BUCKET tier with a null leakRatePerSecond must fail loudly, not silently default");
    }

    @Test
    void shouldEnforceCorrectlyUnder50ConcurrentThreads() throws InterruptedException {

        TenantConfigCache.TierConfig config = buildTierConfig(50, 0.001);
        String tenantId = UUID.randomUUID().toString();

        AtomicInteger allowed = new AtomicInteger(0);
        int totalRequests = 100;
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

        assertEquals(50, allowed.get(),
                "Expected exactly 50 allowed (capacity), got " + allowed.get());
    }
}