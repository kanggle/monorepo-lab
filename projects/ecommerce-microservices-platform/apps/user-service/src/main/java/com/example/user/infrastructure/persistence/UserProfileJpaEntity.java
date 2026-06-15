package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserProfileJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Outer-axis tenant owning this user (ADR-MONO-030 Step 4, M1; TASK-BE-367).
     * Stamped once at insert from the request tenant context; immutable afterward.
     * Not part of the clean {@code UserProfile} domain model — persistence/event layers only.
     */
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    /**
     * Nullable since ADR-MONO-037 (TASK-BE-388): a profile born from an IAM
     * {@code account.created} lifecycle event carries no raw email/name (the event
     * is emailHash-only), and {@code anonymize()} clears it on {@code account.deleted}
     * (anonymized=true). Populated lazily from the OIDC token / profile-update.
     */
    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String name;

    @Column(length = 50)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Column(length = 500)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    static UserProfileJpaEntity fromDomain(UserProfile profile, String tenantId) {
        UserProfileJpaEntity entity = new UserProfileJpaEntity();
        entity.id = profile.getId();
        entity.tenantId = tenantId;
        entity.userId = profile.getUserId();
        entity.email = profile.getEmail() == null ? null : profile.getEmail().value();
        entity.name = profile.getName();
        entity.nickname = profile.getNickname();
        entity.phone = profile.getPhone();
        entity.profileImageUrl = profile.getProfileImageUrl();
        entity.status = profile.getStatus();
        entity.createdAt = profile.getCreatedAt();
        entity.updatedAt = profile.getUpdatedAt();
        return entity;
    }
}
