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
import java.util.Base64;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimitFilterIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private QuotaTierRepository quotaTierRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String plaintextApiKey;

    @BeforeEach
    void setUp() throws Exception {
        QuotaTier tier = QuotaTier.builder()
                .name("FILTER-TEST-" + System.nanoTime())
                .algorithm(AlgorithmType.FIXED_WINDOW)
                .requestsPerWindow(3)
                .windowSizeSeconds(60)
                .burstMultiplier(BigDecimal.ONE)
                .limitType(LimitEnforcementType.HARD)
                .active(true)
                .build();
        tier = quotaTierRepository.save(tier);

        plaintextApiKey = generateApiKey();
        String apiKeyHash = hashApiKey(plaintextApiKey);

        Tenant tenant = Tenant.builder()
                .name("Filter Test Tenant")
                .email("filtertest-" + System.nanoTime() + "@test.com")
                .apiKeyHash(apiKeyHash)
                .tier(tier)
                .status(TenantStatus.ACTIVE)
                .failStrategy(FailStrategyType.OPEN)
                .build();
        tenantRepository.save(tenant);
    }

    @Test
    void shouldReturnRateLimitHeadersOnAllowedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/test").header("X-Api-Key", plaintextApiKey))
                .andExpect(header().string("X-RateLimit-Limit", "3"))
                .andExpect(header().string("X-RateLimit-Remaining", "2"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().string("X-RateLimit-Algorithm", "FIXED_WINDOW"));
    }

    @Test
    void shouldReturn429AfterExhaustingLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/test").header("X-Api-Key", plaintextApiKey));
        }

        mockMvc.perform(get("/api/v1/test").header("X-Api-Key", plaintextApiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void shouldStillReturn401ForMissingApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/test"))
                .andExpect(status().isUnauthorized());
    }

    private String generateApiKey() {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashApiKey(String apiKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hashBytes);
    }
}