package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.entity.Role;
import com.example.auth.domain.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserJpaMapper 단위 테스트")
class UserJpaMapperTest {

    private final UserJpaMapper mapper = new UserJpaMapper();

    @Test
    @DisplayName("도메인 → JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        User user = User.reconstitute(id, "test@example.com", "hashed", "홍길동",
                Role.CUSTOMER, null, now, now, true);

        UserJpaEntity entity = mapper.toEntity(user);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getEmail()).isEqualTo("test@example.com");
        assertThat(entity.getPasswordHash()).isEqualTo("hashed");
        assertThat(entity.getName()).isEqualTo("홍길동");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        assertThat(entity.isActive()).isTrue();
    }

    @Test
    @DisplayName("JpaEntity → 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        User original = User.reconstitute(id, "test@example.com", "hashed", "홍길동",
                Role.CUSTOMER, null, now, now, false);
        UserJpaEntity entity = mapper.toEntity(original);

        User restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(id);
        assertThat(restored.getEmail().value()).isEqualTo("test@example.com");
        assertThat(restored.getPasswordHash()).isEqualTo("hashed");
        assertThat(restored.getName()).isEqualTo("홍길동");
        assertThat(restored.getCreatedAt()).isEqualTo(now);
        assertThat(restored.getUpdatedAt()).isEqualTo(now);
        assertThat(restored.isActive()).isFalse();
    }

    @Test
    @DisplayName("도메인 → JpaEntity → 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        User original = User.create("test@example.com", "encoded-pw", "홍길동");

        UserJpaEntity entity = mapper.toEntity(original);
        User restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getEmail()).isEqualTo(original.getEmail());
        assertThat(restored.getPasswordHash()).isEqualTo(original.getPasswordHash());
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        assertThat(restored.isActive()).isEqualTo(original.isActive());
    }
}
