package com.example.search.domain.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantContext 단위 테스트")
class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("set 후 currentTenant는 설정값을 반환한다")
    void set_validTenantId_currentTenantReturnsIt() {
        TenantContext.set("store-a");

        assertThat(TenantContext.currentTenant()).isEqualTo("store-a");
    }

    @Test
    @DisplayName("set 없이 currentTenant는 기본값 'ecommerce'를 반환한다")
    void currentTenant_noContextSet_returnsDefault() {
        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("set(null)은 컨텍스트를 지우고 기본값으로 리졸브된다")
    void set_null_clearsContextAndReturnsDefault() {
        TenantContext.set("store-a");
        TenantContext.set(null);

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("set(blank)은 컨텍스트를 지우고 기본값으로 리졸브된다")
    void set_blank_clearsContextAndReturnsDefault() {
        TenantContext.set("store-a");
        TenantContext.set("   ");

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("clear 후 currentTenant는 기본값을 반환한다")
    void clear_afterSet_returnsDefault() {
        TenantContext.set("store-a");
        TenantContext.clear();

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("DEFAULT_TENANT_ID 상수는 'ecommerce'이다")
    void defaultTenantId_isEcommerce() {
        assertThat(TenantContext.DEFAULT_TENANT_ID).isEqualTo("ecommerce");
    }
}
