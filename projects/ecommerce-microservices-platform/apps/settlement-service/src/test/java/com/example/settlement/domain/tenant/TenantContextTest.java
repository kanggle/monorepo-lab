package com.example.settlement.domain.tenant;

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
    @DisplayName("TASK-BE-564 — 앞뒤 공백이 포함된 tenant id 는 trim 되지 않고 그대로 저장된다 " +
            "(9개 형제 서비스의 TenantContext 와 byte-identical 이어야 함)")
    void set_whitespacePadded_isPreservedVerbatim() {
        TenantContext.set(" tenant-a ");

        assertThat(TenantContext.currentTenant()).isEqualTo(" tenant-a ");
    }

    @Test
    @DisplayName("TASK-BE-564 — 앞쪽 공백만 있어도 그대로 저장된다")
    void set_leadingWhitespaceOnly_isPreservedVerbatim() {
        TenantContext.set(" tenant-a");

        assertThat(TenantContext.currentTenant()).isEqualTo(" tenant-a");
    }

    @Test
    @DisplayName("TASK-BE-564 — 뒤쪽 공백만 있어도 그대로 저장된다")
    void set_trailingWhitespaceOnly_isPreservedVerbatim() {
        TenantContext.set("tenant-a ");

        assertThat(TenantContext.currentTenant()).isEqualTo("tenant-a ");
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
