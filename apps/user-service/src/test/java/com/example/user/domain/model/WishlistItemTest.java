package com.example.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WishlistItem 도메인 모델 테스트")
class WishlistItemTest {

    @Test
    @DisplayName("create로 새로운 WishlistItem을 생성한다")
    void create_validArgs_createsItem() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        WishlistItem item = WishlistItem.create(userId, productId);

        assertThat(item.getId()).isNotNull();
        assertThat(item.getUserId()).isEqualTo(userId);
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getAddedAt()).isNotNull();
    }

    @Test
    @DisplayName("userId가 null이면 IllegalArgumentException이 발생한다")
    void create_nullUserId_throwsException() {
        assertThatThrownBy(() -> WishlistItem.create(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");
    }

    @Test
    @DisplayName("productId가 null이면 IllegalArgumentException이 발생한다")
    void create_nullProductId_throwsException() {
        assertThatThrownBy(() -> WishlistItem.create(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product ID");
    }

    @Test
    @DisplayName("reconstitute로 기존 데이터를 복원한다")
    void reconstitute_validArgs_reconstructsItem() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant addedAt = Instant.now();

        WishlistItem item = WishlistItem.reconstitute(id, userId, productId, addedAt);

        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.getUserId()).isEqualTo(userId);
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getAddedAt()).isEqualTo(addedAt);
    }
}
