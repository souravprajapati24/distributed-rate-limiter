package com.ratelimiter.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AlgorithmSelectionEndToEndTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ratelimiter_test")
            .withUsername("ratelimiter")
            .withPassword("secret");

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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    private record TenantHandle(String apiKey, String tenantId) {}

    private String findTierId(String tierName) {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/api/v1/tiers"), String.class);
        try {
            JsonNode tiers = objectMapper.readTree(response.getBody());
            for (JsonNode tier : tiers) {
                if (tierName.equals(tier.get("name").asText())) {
                    return tier.get("id").asText();
                }
            }
        } catch (Exception e) {
            fail("Failed to parse /api/v1/tiers response: " + e.getMessage());
        }
        throw new IllegalStateException("Tier not found: " + tierName + " — was V8/V9 seed data applied?");
    }

    private TenantHandle registerTenant(String tierName, String emailPrefix) {
        String tierId = findTierId(tierName);
        String body = String.format(
                "{\"name\":\"%s Test\",\"email\":\"%s-%d@test.com\",\"tierId\":\"%s\"}",
                tierName, emailPrefix, System.nanoTime(), tierId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl("/api/v1/tenants"), new HttpEntity<>(body, headers), String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        try {
            JsonNode json = objectMapper.readTree(response.getBody());
            return new TenantHandle(json.get("apiKey").asText(), json.get("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse tenant creation response", e);
        }
    }

    private ResponseEntity<String> callProtectedEndpoint(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Api-Key", apiKey);
        return restTemplate.exchange(
                baseUrl("/api/v1/test"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    void freeTierTenantIsEnforcedWithFixedWindow() {
        TenantHandle tenant = registerTenant("FREE", "free");
        ResponseEntity<String> response = callProtectedEndpoint(tenant.apiKey());

        assertEquals("FIXED_WINDOW", response.getHeaders().getFirst("X-RateLimit-Algorithm"));
        assertNotNull(response.getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    @Test
    void starterTierTenantIsEnforcedWithSlidingWindow() {
        TenantHandle tenant = registerTenant("STARTER", "starter");
        ResponseEntity<String> response = callProtectedEndpoint(tenant.apiKey());

        assertEquals("SLIDING_WINDOW", response.getHeaders().getFirst("X-RateLimit-Algorithm"));
    }

    @Test
    void growthTierTenantIsEnforcedWithTokenBucket() {
        TenantHandle tenant = registerTenant("GROWTH", "growth");
        ResponseEntity<String> response = callProtectedEndpoint(tenant.apiKey());

        assertEquals("TOKEN_BUCKET", response.getHeaders().getFirst("X-RateLimit-Algorithm"));
    }

    @Test
    void internalDownstreamTenantIsEnforcedWithLeakyBucket() {
        TenantHandle tenant = registerTenant("INTERNAL_DOWNSTREAM", "internal");
        ResponseEntity<String> response = callProtectedEndpoint(tenant.apiKey());

        assertEquals("LEAKY_BUCKET", response.getHeaders().getFirst("X-RateLimit-Algorithm"));
    }

    @Test
    void tenantsOnDifferentAlgorithmsEnforceWithZeroCrossInterference() {
        TenantHandle fixedWindowTenant = registerTenant("FREE", "isolation-fw");
        TenantHandle tokenBucketTenant = registerTenant("GROWTH", "isolation-tb");

        ResponseEntity<String> fwResponse1 = callProtectedEndpoint(fixedWindowTenant.apiKey());
        ResponseEntity<String> fwResponse2 = callProtectedEndpoint(fixedWindowTenant.apiKey());

        int fwRemaining1 = Integer.parseInt(fwResponse1.getHeaders().getFirst("X-RateLimit-Remaining"));
        int fwRemaining2 = Integer.parseInt(fwResponse2.getHeaders().getFirst("X-RateLimit-Remaining"));
        assertEquals(fwRemaining1 - 1, fwRemaining2,
                "FREE tenant's own counter should decrease by exactly 1 per request");

        ResponseEntity<String> tbResponse = callProtectedEndpoint(tokenBucketTenant.apiKey());
        assertEquals("TOKEN_BUCKET", tbResponse.getHeaders().getFirst("X-RateLimit-Algorithm"));
        int tbRemaining = Integer.parseInt(tbResponse.getHeaders().getFirst("X-RateLimit-Remaining"));

        assertEquals(19999, tbRemaining,
                "GROWTH tenant's Token Bucket must be completely full minus this one request, " +
                        "proving zero interference from the FREE tenant's Fixed Window activity");
    }
}