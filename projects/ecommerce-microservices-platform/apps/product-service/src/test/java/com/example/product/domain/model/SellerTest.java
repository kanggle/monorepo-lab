package com.example.product.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Seller 애그리거트 단위 테스트 (ADR-MONO-030 §3.1)")
class SellerTest {

    @Test
    @DisplayName("register 시 ACTIVE 상태로 생성된다")
    void register_createsActiveSeller() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");

        assertThat(seller.getSellerId()).isEqualTo("seller-a1");
        assertThat(seller.getDisplayName()).isEqualTo("셀러 A1");
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
        assertThat(seller.isActive()).isTrue();
        assertThat(seller.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("defaultSeller 는 seller_id='default' 의 ACTIVE 셀러 (D8 anchor)")
    void defaultSeller_isDefaultId() {
        Seller seller = Seller.defaultSeller();

        assertThat(seller.getSellerId()).isEqualTo(Seller.DEFAULT_SELLER_ID);
        assertThat(seller.getSellerId()).isEqualTo("default");
        assertThat(seller.isActive()).isTrue();
    }

    @Test
    @DisplayName("seller_id 가 blank 면 예외")
    void register_blankSellerId_throws() {
        assertThatThrownBy(() -> Seller.register("  ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("displayName 이 blank 면 예외")
    void register_blankDisplayName_throws() {
        assertThatThrownBy(() -> Seller.register("s-1", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reconstitute 로 영속 상태에서 복원된다")
    void reconstitute_restoresState() {
        java.time.Instant now = java.time.Instant.now();
        Seller seller = Seller.reconstitute("s-1", "셀러", SellerStatus.ACTIVE, now, now);

        assertThat(seller.getSellerId()).isEqualTo("s-1");
        assertThat(seller.getStatus()).isEqualTo(SellerStatus.ACTIVE);
    }
}
