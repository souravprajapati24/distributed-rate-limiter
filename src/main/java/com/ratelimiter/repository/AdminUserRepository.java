package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}