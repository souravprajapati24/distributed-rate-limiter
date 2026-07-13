package com.ratelimiter.filter;

import com.ratelimiter.domain.entity.QuotaTier;
import com.ratelimiter.domain.entity.Tenant;
import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.domain.enums.FailStrategyType;
import com.ratelimiter.domain.enums.LimitEnforcementType;
import com.ratelimiter.domain.enums.TenantStatus;
import com.ratelimiter.repository.QuotaTierRepository;
import com.ratelimiter.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import static org.mockito.AdditionalMatchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RedisFailoverIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ratelimiter").withUsername("seceret").withPassword("postgres");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private QuotaTierRepository quotaTierRepository;
    @Autowired private TenantRepository tenantRepository;

    private String openApiKey;
    private String closedApiKey;

    @BeforeEach
    void setUp() throws Exception {
        QuotaTier tier = quotaTierRepository.save(QuotaTier.builder()
                .name("FAILOVER-TEST-" + System.nanoTime())
                .algorithm(AlgorithmType.FIXED_WINDOW)
                .requestsPerWindow(100)
                .windowSizeSeconds(60)
                .burstMultiplier(BigDecimal.ONE)
                .limitType(LimitEnforcementType.HARD)
                .active(true)
                .build());

        openApiKey = generateApiKey();
        tenantRepository.save(Tenant.builder()
                .name("Open Strategy Tenant")
                .email("open-" + System.nanoTime() + "@test.com")
                .apiKeyHash(hashApiKey(openApiKey))
                .tier(tier)
                .status(TenantStatus.ACTIVE)
                .failStrategy(FailStrategyType.OPEN)
                .build());

        closedApiKey = generateApiKey();
        tenantRepository.save(Tenant.builder()
                .name("Closed Strategy Tenant")
                .email("closed-" + System.nanoTime() + "@test.com")
                .apiKeyHash(hashApiKey(closedApiKey))
                .tier(tier)
                .status(TenantStatus.ACTIVE)
                .failStrategy(FailStrategyType.CLOSED)
                .build());

        mockMvc.perform(get("/api/v1/test").header("X-Api-Key", openApiKey));
        mockMvc.perform(get("/api/v1/test").header("X-Api-Key", closedApiKey));
    }

    @Test
    void openFailStrategyAllowsRequestsThroughDuringRedisOutage() throws Exception {
        redis.stop();
        try {
            mockMvc.perform(get("/api/v1/test").header("X-Api-Key", openApiKey))
                    .andExpect(status().isOk());
        } finally {
            redis.start();
        }
    }

    @Test
    void closedFailStrategyReturns503DuringRedisOutage() throws Exception {
        redis.stop();
        try {
            mockMvc.perform(get("/api/v1/probe").header("X-Api-Key", closedApiKey))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            redis.start();
        }
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashApiKey(String apiKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}