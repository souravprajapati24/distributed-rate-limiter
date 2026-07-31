package com.ratelimiter.service;

import com.ratelimiter.domain.entity.AdminUser;
import com.ratelimiter.domain.enums.AdminRole;
import com.ratelimiter.dto.request.AuthRegisterRequest;
import com.ratelimiter.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Admin user not found: " + username));
    }


    @Transactional
    public AdminUser registerAdmin(AuthRegisterRequest request) {
        if (adminUserRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException(
                    "Username already taken: " + request.username());
        }
        if (adminUserRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.email());
        }

        AdminRole role = (request.role() != null)
                ? AdminRole.valueOf(request.role().toUpperCase())
                : AdminRole.ROLE_OPERATOR;

        AdminUser user = AdminUser.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .active(true)
                .build();

        AdminUser saved = adminUserRepository.save(user);
        log.info("Registered new admin user: {} (role={})", saved.getUsername(), saved.getRole());
        return saved;
    }

    @Transactional
    public void recordLogin(UUID userId) {
        adminUserRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(java.time.Instant.now());
            adminUserRepository.save(user);
        });
    }
}