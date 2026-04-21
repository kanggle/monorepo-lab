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

    @Column(nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
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

    static UserProfileJpaEntity fromDomain(UserProfile profile) {
        UserProfileJpaEntity entity = new UserProfileJpaEntity();
        entity.id = profile.getId();
        entity.userId = profile.getUserId();
        entity.email = profile.getEmail().value();
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
