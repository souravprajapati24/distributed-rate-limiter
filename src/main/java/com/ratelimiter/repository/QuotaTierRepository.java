package com.ratelimiter.repository;

import com.ratelimiter.domain.entity.QuotaTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuotaTierRepository extends JpaRepository<QuotaTier, UUID> {


    Optional<QuotaTier> findByName(String name);

    List<QuotaTier> findByActiveTrue();

    boolean existsByName(String name);
}