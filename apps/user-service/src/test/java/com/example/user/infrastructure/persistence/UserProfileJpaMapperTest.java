package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProfileJpaMapper 단위 테스트")
class UserProfileJpaMapperTest {

    private final UserProfileJpaMapper mapper = new UserProfileJpaMapper();

    @Test
    @DisplayName("도메인 → JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UserProfile profile = UserProfile.reconstitute(id, userId, "test@example.com", "홍길동",
                "길동이", "010-1234-5678", "https://img.example.com/pic.jpg",
                ProfileStatus.ACTIVE, now, now);

        UserProfileJpaEntity entity = mapper.toEntity(profile);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getEmail()).isEqualTo("test@example.com");
        assertThat(entity.getName()).isEqualTo("홍길동");
        assertThat(entity.getNickname()).isEqualTo("길동이");
        assertThat(entity.getPhone()).isEqualTo("010-1234-5678");
        assertThat(entity.getProfileImageUrl()).isEqualTo("https://img.example.com/pic.jpg");
        assertThat(entity.getStatus()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("JpaEntity → 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UserProfile original = UserProfile.reconstitute(id, userId, "test@example.com", "홍길동",
                null, null, null, ProfileStatus.SUSPENDED, now, now);
        UserProfileJpaEntity entity = mapper.toEntity(original);

        UserProfile restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(id);
        assertThat(restored.getUserId()).isEqualTo(userId);
        assertThat(restored.getEmail().value()).isEqualTo("test@example.com");
        assertThat(restored.getName()).isEqualTo("홍길동");
        assertThat(restored.getNickname()).isNull();
        assertThat(restored.getPhone()).isNull();
        assertThat(restored.getProfileImageUrl()).isNull();
        assertThat(restored.getStatus()).isEqualTo(ProfileStatus.SUSPENDED);
        assertThat(restored.getCreatedAt()).isEqualTo(now);
        assertThat(restored.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("도메인 → JpaEntity → 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        UUID userId = UUID.randomUUID();
        UserProfile original = UserProfile.create(userId, "test@example.com", "홍길동");

        UserProfileJpaEntity entity = mapper.toEntity(original);
        UserProfile restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getUserId()).isEqualTo(original.getUserId());
        assertThat(restored.getEmail()).isEqualTo(original.getEmail());
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getNickname()).isEqualTo(original.getNickname());
        assertThat(restored.getPhone()).isEqualTo(original.getPhone());
        assertThat(restored.getProfileImageUrl()).isEqualTo(original.getProfileImageUrl());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }
}
