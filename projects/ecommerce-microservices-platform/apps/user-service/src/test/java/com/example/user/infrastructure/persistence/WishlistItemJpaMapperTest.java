package com.example.user.infrastructure.persistence;

import com.example.user.domain.model.WishlistItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WishlistItemJpaMapper 단위 테스트")
class WishlistItemJpaMapperTest {

    private final WishlistItemJpaMapper mapper = new WishlistItemJpaMapper();

    @Test
    @DisplayName("도메인 → JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant addedAt = Instant.now();
        WishlistItem item = WishlistItem.reconstitute(id, userId, productId, addedAt);

        WishlistItemJpaEntity entity = mapper.toEntity(item);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getProductId()).isEqualTo(productId);
        assertThat(entity.getAddedAt()).isEqualTo(addedAt);
    }

    @Test
    @DisplayName("JpaEntity → 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant addedAt = Instant.now();
        WishlistItem original = WishlistItem.reconstitute(id, userId, productId, addedAt);
        WishlistItemJpaEntity entity = mapper.toEntity(original);

        WishlistItem restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(id);
        assertThat(restored.getUserId()).isEqualTo(userId);
        assertThat(restored.getProductId()).isEqualTo(productId);
        assertThat(restored.getAddedAt()).isEqualTo(addedAt);
    }

    @Test
    @DisplayName("도메인 → JpaEntity → 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        WishlistItem original = WishlistItem.create(UUID.randomUUID(), UUID.randomUUID());

        WishlistItemJpaEntity entity = mapper.toEntity(original);
        WishlistItem restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getUserId()).isEqualTo(original.getUserId());
        assertThat(restored.getProductId()).isEqualTo(original.getProductId());
        assertThat(restored.getAddedAt()).isEqualTo(original.getAddedAt());
    }
}
