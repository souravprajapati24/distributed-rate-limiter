package com.ratelimiter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("ratelimiter_db").withUsername("ratelimiter").withPassword("secret");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add(
                "rate-limiter.jwt.secret",
                () -> "this-is-a-dev-only-secret-replace-in-prod-must-be-32-chars"
        );
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;


    @Test
    void login_withValidCredentials_shouldReturnJwt() throws Exception {
        // Uses the bootstrap admin seeded by V13
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sourav\",\"password\":\"Sourav@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.username").value("sourav"));
    }

    @Test
    void login_withWrongPassword_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sourav\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_withUnknownUser_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nonexistent\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void accessProtectedEndpoint_withoutJwt_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/v1/tiers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_withValidAdminJwt_shouldSucceed() throws Exception {
        String token = obtainAdminJwt();
        mockMvc.perform(get("/api/v1/tiers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void accessProtectedEndpoint_withExpiredJwt_shouldReturn401() throws Exception {
        String expiredToken = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJzdWIiOiJhZG1pbiIsImV4cCI6MTYwMDAwMDAwMH0."
                + "invalid-signature";

        mockMvc.perform(get("/api/v1/tiers")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }


    @Test
    void register_withoutAdminJwt_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newop\",\"email\":\"newop@test.com\"," +
                                "\"password\":\"password123\",\"role\":\"ROLE_OPERATOR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withAdminJwt_shouldCreate201() throws Exception {
        String token = obtainAdminJwt();
        String unique = String.valueOf(System.nanoTime());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"username\":\"operator" + unique + "\","
                                + "\"email\":\"op" + unique + "@test.com\","
                                + "\"password\":\"securepass1\","
                                + "\"role\":\"ROLE_OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("operator" + unique));
    }


    @Test
    void rateLimitedEndpoint_doesNotRequireJwt_onlyXApiKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertNotNull(body);
        assert body.contains("MISSING_API_KEY") :
                "Expected MISSING_API_KEY from RateLimitFilter, got: " + body;
    }


    private String obtainAdminJwt() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sourav\",\"password\":\"Sourav@123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("accessToken");
    }
}