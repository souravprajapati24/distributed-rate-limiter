package com.ratelimiter.domain.entity;

import com.ratelimiter.domain.enums.AlgorithmType;
import com.ratelimiter.domain.enums.LimitEnforcementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "tenant_quota_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantQuotaOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tenant_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_overrides_tenant_id")
    )
    private Tenant tenant;


    @Column(name = "endpoint_pattern", nullable = false, length = 255)
    private String endpointPattern;

    @Column(name = "requests_per_window", nullable = false)
    private int requestsPerWindow;

    @Column(name = "window_size_seconds", nullable = false)
    private int windowSizeSeconds;


    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "algorithm_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private AlgorithmType algorithm;


    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", columnDefinition = "limit_enforcement_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private LimitEnforcementType limitType;


    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = Instant.now(); }

}