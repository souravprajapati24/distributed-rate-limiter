package com.ratelimiter.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT Bearer token. Obtain by calling POST /api/v1/auth/login"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RateGuard API")
                        .version("1.0.0")
                        .description(
                                "Distributed Rate Limiting and Quota Management Platform\n\n" +
                                        "### Key Features\n" +
                                        "- Multi-tenant rate limiting with 4 algorithms (Fixed Window, Sliding Window, Token Bucket, Leaky Bucket)\n" +
                                        "- Per-tenant and per-endpoint quota configuration\n" +
                                        "- JWT-based authentication with role-based access control (ADMIN, OPERATOR)\n" +
                                        "- Comprehensive audit logging and usage analytics\n" +
                                        "- Redis-backed distributed enforcement with PostgreSQL persistence\n\n" +
                                        "### Authentication\n" +
                                        "All endpoints except `/api/v1/auth/login`, `/api/v1/test`, and `/actuator/**` require a valid JWT Bearer token.\n" +
                                        "1. Call `POST /api/v1/auth/login` with credentials to obtain a token\n" +
                                        "2. Include token in subsequent requests: `Authorization: Bearer <token>`\n\n" +
                                        "### Roles\n" +
                                        "- **ADMIN**: Full access to all operations (create, update, delete, suspend, rotate keys, etc.)\n" +
                                        "- **OPERATOR**: Read-only access (view tenants, tiers, usage, audit logs)\n\n" +
                                        "### Pagination\n" +
                                        "Endpoints supporting pagination accept `page` (0-indexed, default: 0) and `size` (default: 20) query parameters.\n" +
                                        "Responses include `totalElements`, `totalPages`, and `content` array.\n"
                        )
                        .contact(new Contact()
                                .name("Support Team")
                                .email("sourav7206672617@gmail.com")
                        )

                )
                .servers(Arrays.asList(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("yet to add") // TODO : have to add production address
                                .description("Production server")
                ));
    }
}