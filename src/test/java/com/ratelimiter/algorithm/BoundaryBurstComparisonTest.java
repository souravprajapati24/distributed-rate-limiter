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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class BoundaryBurstComparisonTest {

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
    private FixedWindowAlgorithm fixedWindowAlgorithm;

    @Autowired
    private SlidingWindowAlgorithm slidingWindowAlgorithm;

    private static final int LIMIT = 10;
    private static final int WINDOW_SECONDS = 2;

    private TenantConfigCache.TierConfig buildTierConfig(String algorithmName) {
        return new TenantConfigCache.TierConfig(
                UUID.randomUUID(), algorithmName, LIMIT, WINDOW_SECONDS,
                BigDecimal.ONE, null, "HARD");
    }

    @Test
    void fixedWindowAllowsRoughlyDoubleLimitAcrossAWindowBoundary() throws InterruptedException {
        TenantConfigCache.TierConfig config = buildTierConfig("FIXED_WINDOW");
        String tenantId = UUID.randomUUID().toString();

        alignToNearWindowBoundary(WINDOW_SECONDS);

        int firstBurstAllowed = fireBurstAndCountAllowed(fixedWindowAlgorithm, tenantId, config, LIMIT);
        assertEquals(LIMIT, firstBurstAllowed, "First burst should consume the entire current window's quota");

        Thread.sleep((WINDOW_SECONDS * 1000L) - (System.currentTimeMillis() % (WINDOW_SECONDS * 1000L)) + 50);

        int secondBurstAllowed = fireBurstAndCountAllowed(fixedWindowAlgorithm, tenantId, config, LIMIT);

        int totalAllowedAcrossBothBursts = firstBurstAllowed + secondBurstAllowed;

        assertTrue(totalAllowedAcrossBothBursts > LIMIT,
                "Fixed Window is EXPECTED to allow more than " + LIMIT +
                        " requests across a window boundary (boundary burst flaw). Got: " + totalAllowedAcrossBothBursts);
        assertEquals(LIMIT, secondBurstAllowed,
                "The second burst, landing entirely within the new window, should independently allow the full limit again");
    }

    @Test
    void slidingWindowDoesNotExhibitTheSameOverAdmission() throws InterruptedException {
        TenantConfigCache.TierConfig config = buildTierConfig("SLIDING_WINDOW");
        String tenantId = UUID.randomUUID().toString();

        int firstBurstAllowed = fireBurstAndCountAllowed(slidingWindowAlgorithm, tenantId, config, LIMIT);
        assertEquals(LIMIT, firstBurstAllowed);

        int secondBurstAllowed = fireBurstAndCountAllowed(slidingWindowAlgorithm, tenantId, config, LIMIT);

        int totalAllowedWithinTheRollingWindow = firstBurstAllowed + secondBurstAllowed;

        assertEquals(LIMIT, totalAllowedWithinTheRollingWindow,
                "Sliding Window must enforce exactly " + LIMIT +
                        " requests within any rolling " + WINDOW_SECONDS + "-second window, " +
                        "regardless of how the requests are split across two bursts. Got: " +
                        totalAllowedWithinTheRollingWindow);
    }

    private int fireBurstAndCountAllowed(RateLimitAlgorithm algorithm, String tenantId,
                                         TenantConfigCache.TierConfig config, int burstSize) {
        AtomicInteger allowed = new AtomicInteger(0);
        for (int i = 0; i < burstSize; i++) {
            RateLimitDecision d = algorithm.evaluate(tenantId, "api:boundary-test", config);
            if (d.allowed()) allowed.incrementAndGet();
        }
        return allowed.get();
    }

    private void alignToNearWindowBoundary(int windowSeconds) throws InterruptedException {
        long windowMs = windowSeconds * 1000L;
        long msIntoCurrentWindow = System.currentTimeMillis() % windowMs;
        long msUntilNextWindow = windowMs - msIntoCurrentWindow;

        if (msUntilNextWindow > 200) {
            Thread.sleep(msUntilNextWindow - 200);
        }
    }
}