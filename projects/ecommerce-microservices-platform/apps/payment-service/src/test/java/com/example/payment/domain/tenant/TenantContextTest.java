package com.example.payment.domain.tenant;

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
    @DisplayName("컨텍스트 미설정 시 currentTenant() 는 기본 테넌트 'ecommerce' 를 반환한다 (D8 net-zero)")
    void currentTenant_noContextSet_returnsDefaultTenant() {
        TenantContext.clear();

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("set() 호출 후 currentTenant() 는 설정된 값을 반환한다")
    void currentTenant_afterSet_returnsSetValue() {
        TenantContext.set("tenant-a");

        assertThat(TenantContext.currentTenant()).isEqualTo("tenant-a");
    }

    @Test
    @DisplayName("null 로 set() 호출 시 currentTenant() 는 기본값을 반환한다")
    void set_null_clearsContext() {
        TenantContext.set("tenant-a");
        TenantContext.set(null);

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("빈 문자열로 set() 호출 시 currentTenant() 는 기본값을 반환한다")
    void set_blank_clearsContext() {
        TenantContext.set("tenant-a");
        TenantContext.set("   ");

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("clear() 호출 후 currentTenant() 는 기본값을 반환한다")
    void clear_resetsToDefault() {
        TenantContext.set("tenant-a");
        TenantContext.clear();

        assertThat(TenantContext.currentTenant()).isEqualTo("ecommerce");
    }

    @Test
    @DisplayName("DEFAULT_TENANT_ID 상수는 'ecommerce' 이다")
    void defaultTenantId_constant_isEcommerce() {
        assertThat(TenantContext.DEFAULT_TENANT_ID).isEqualTo("ecommerce");
    }
}
