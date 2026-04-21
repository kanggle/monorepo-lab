package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.UserProfile;
import org.springframework.stereotype.Component;

@Component
class UserProfileJpaMapper {

    UserProfile toDomain(UserProfileJpaEntity entity) {
        return UserProfile.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getName(),
                entity.getNickname(),
                entity.getPhone(),
                entity.getProfileImageUrl(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    UserProfileJpaEntity toEntity(UserProfile profile) {
        return UserProfileJpaEntity.fromDomain(profile);
    }
}
