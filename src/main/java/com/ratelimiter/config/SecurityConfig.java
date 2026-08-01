package com.ratelimiter.config;

import com.ratelimiter.filter.JwtAuthenticationFilter;
import com.ratelimiter.service.AdminUserService;
import com.ratelimiter.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AdminUserService adminUserService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                . exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write(
                            "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}"
                    );
                }))


                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/v1/test").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/suspend").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/reactivate").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/rotate-key").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/v1/tiers").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/tiers/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/tenants/*/overrides").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/tenants/*/overrides/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/tenants/*/tier").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.GET, "/api/v1/tenants/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tiers/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/usage/**").hasAnyRole("ADMIN", "OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/audit/**").hasAnyRole("ADMIN", "OPERATOR")

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService, adminUserService),
                        UsernamePasswordAuthenticationFilter.class)

                .authenticationProvider(authenticationProvider());

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

}