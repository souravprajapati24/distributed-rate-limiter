package com.ratelimiter.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "api_key_rotations")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeyRotation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "old_key_hash", nullable = false, updatable = false, length = 64)
    private String oldKeyHash;

    @Column(name = "new_key_hash", nullable = false, updatable = false, length = 64)
    private String newKeyHash;

    @Column(name = "rotated_by", nullable = false, updatable = false, length = 255)
    private String rotatedBy;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "rotated_at", nullable = false, updatable = false)
    private Instant rotatedAt;

    @PrePersist
    protected void onCreate() {
        if (rotatedAt == null) {
            rotatedAt = Instant.now();
        }
    }
}