package com.ratelimiter.domain.entity;

import com.ratelimiter.domain.enums.FailStrategyType;
import com.ratelimiter.domain.enums.TenantStatus;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 64)
    private String apiKeyHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tier_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_tenants_tier_id")
    )
    private QuotaTier tier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "tenant_status")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;


    @Enumerated(EnumType.STRING)
    @Column(name = "fail_strategy", nullable = false, columnDefinition = "fail_strategy_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private FailStrategyType failStrategy = FailStrategyType.OPEN;


    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspended_by", length = 255)
    private String suspendedBy;

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TenantQuotaOverride> quotaOverrides = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }



    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == TenantStatus.SUSPENDED;
    }


    public void suspend(String adminUsername) {
        this.status      = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
        this.suspendedBy = adminUsername;
    }


    public void reactivate() {
        this.status      = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspendedBy = null;
    }
}