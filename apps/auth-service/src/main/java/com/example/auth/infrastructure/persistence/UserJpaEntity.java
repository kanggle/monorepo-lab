package com.example.auth.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class UserJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = true)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 50)
    private String oauthProvider;

    static UserJpaEntity fromDomain(com.example.auth.domain.entity.User user) {
        UserJpaEntity entity = new UserJpaEntity();
        entity.id = user.getId();
        entity.email = user.getEmail().value();
        entity.passwordHash = user.getPasswordHash();
        entity.name = user.getName();
        entity.role = user.getRole().name();
        entity.oauthProvider = user.getOauthProvider();
        entity.createdAt = user.getCreatedAt();
        entity.updatedAt = user.getUpdatedAt();
        entity.active = user.isActive();
        return entity;
    }
}
