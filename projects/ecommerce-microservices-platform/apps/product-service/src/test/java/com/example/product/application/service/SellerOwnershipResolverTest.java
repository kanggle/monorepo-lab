package com.example.product.application.service;

import com.example.product.domain.model.Seller;
import com.example.product.domain.seller.SellerScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seller ownership resolution unit test (ADR-MONO-030 §3.2). Resolution order:
 * explicit request seller → restricted scope claim → tenant default seller (D8).
 */
@DisplayName("SellerOwnershipResolver 단위 테스트 — 상품 소유 셀러 결정")
class SellerOwnershipResolverTest {

    private final SellerOwnershipResolver resolver = new SellerOwnershipResolver();

    @AfterEach
    void clear() {
        SellerScopeContext.clear();
    }

    @Test
    @DisplayName("명시 request sellerId 가 최우선")
    void explicitRequestSeller_wins() {
        SellerScopeContext.set("scope-seller");

        assertThat(resolver.resolveForRegister("explicit-seller")).isEqualTo("explicit-seller");
    }

    @Test
    @DisplayName("명시 없으면 restricted 스코프 클레임으로 귀속")
    void restrictedScope_usedWhenNoExplicit() {
        SellerScopeContext.set("scope-seller");

        assertThat(resolver.resolveForRegister(null)).isEqualTo("scope-seller");
        assertThat(resolver.resolveForRegister("  ")).isEqualTo("scope-seller");
    }

    @Test
    @DisplayName("명시도 없고 스코프도 없으면 default seller (D8, net-zero)")
    void noExplicitNoScope_resolvesToDefault() {
        // no scope bound
        assertThat(resolver.resolveForRegister(null)).isEqualTo(Seller.DEFAULT_SELLER_ID);
    }

    @Test
    @DisplayName("스코프가 '*' (unrestricted) 면 명시 없을 때 default seller")
    void wildcardScope_resolvesToDefault() {
        SellerScopeContext.set("*");

        assertThat(resolver.resolveForRegister(null)).isEqualTo(Seller.DEFAULT_SELLER_ID);
    }

    @Test
    @DisplayName("request sellerId 는 trim 된다")
    void requestSeller_isTrimmed() {
        assertThat(resolver.resolveForRegister("  s-1 ")).isEqualTo("s-1");
    }
}
