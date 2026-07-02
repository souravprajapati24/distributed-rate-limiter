package com.ratelimiter.domain.entity;

import com.ratelimiter.domain.enums.GranularityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "usage_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "granularity_type")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private GranularityType granularity;


    @Column(nullable = false)
    @Builder.Default
    private long allowed = 0L;

    @Column(nullable = false)
    @Builder.Default
    private long denied = 0L;


    @Column(insertable = false, updatable = false)
    private long total;


    @Column(name = "denial_rate", insertable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal denialRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = Instant.now(); }

}