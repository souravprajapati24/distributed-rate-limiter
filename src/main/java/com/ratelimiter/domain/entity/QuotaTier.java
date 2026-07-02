package com.ratelimiter.domain.entity;

import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.domain.enums.LimitEnforcementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quota_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotaTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "algorithm_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AlgorithmType algorithm;


    @Column(name = "requests_per_window", nullable = false)
    private int requestsPerWindow;

    @Column(name = "window_size_seconds", nullable = false)
    private int windowSizeSeconds;

    @Column(name = "burst_multiplier", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal burstMultiplier = BigDecimal.ONE;


    @Column(name = "leak_rate_per_second", precision = 10, scale = 2)
    private BigDecimal leakRatePerSecond;


    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, columnDefinition = "limit_enforcement_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private LimitEnforcementType limitType = LimitEnforcementType.HARD;


    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

}