package com.example.product.domain.seller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller-scope ABAC context unit test (ADR-MONO-030 §3.3, ADR-MONO-025 net-zero).
 * Restricted = a concrete seller; absent / blank / '*' = unrestricted (fail-OPEN).
 */
@DisplayName("SellerScopeContext 단위 테스트 — net-zero ABAC 셀러 스코프")
class SellerScopeContextTest {

    @AfterEach
    void clear() {
        SellerScopeContext.clear();
    }

    @Test
    @DisplayName("구체 seller_id 바인딩 시 RESTRICTED — 그 셀러로 필터")
    void concreteScope_isRestricted() {
        SellerScopeContext.set("seller-a1");

        assertThat(SellerScopeContext.isRestricted()).isTrue();
        assertThat(SellerScopeContext.currentSellerScope()).isEqualTo("seller-a1");
    }

    @Test
    @DisplayName("스코프 부재 = UNRESTRICTED (net-zero, 무필터)")
    void absentScope_isUnrestricted() {
        // nothing bound
        assertThat(SellerScopeContext.isRestricted()).isFalse();
        assertThat(SellerScopeContext.currentSellerScope()).isNull();
    }

    @Test
    @DisplayName("빈/공백 스코프 = UNRESTRICTED (net-zero)")
    void blankScope_isUnrestricted() {
        SellerScopeContext.set("   ");

        assertThat(SellerScopeContext.isRestricted()).isFalse();
        assertThat(SellerScopeContext.currentSellerScope()).isNull();
    }

    @Test
    @DisplayName("와일드카드 '*' = UNRESTRICTED (restricted 아님, fail-OPEN)")
    void wildcardScope_isUnrestricted() {
        SellerScopeContext.set("*");

        assertThat(SellerScopeContext.isRestricted()).isFalse();
        assertThat(SellerScopeContext.currentSellerScope()).isNull();
    }

    @Test
    @DisplayName("스코프 값은 trim 된다")
    void scope_isTrimmed() {
        SellerScopeContext.set("  seller-a2  ");

        assertThat(SellerScopeContext.currentSellerScope()).isEqualTo("seller-a2");
    }

    @Test
    @DisplayName("clear 후 다시 UNRESTRICTED 로 복귀 (스레드 누수 방지)")
    void clear_resetsToUnrestricted() {
        SellerScopeContext.set("seller-a1");
        SellerScopeContext.clear();

        assertThat(SellerScopeContext.isRestricted()).isFalse();
    }
}
