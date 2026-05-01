package com.example.admin.application;

import com.example.admin.domain.rbac.AdminOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminOperator} domain record.
 *
 * <p>TASK-BE-249: {@code isPlatformScope()} returns {@code true} only for the
 * exact sentinel value {@link AdminOperator#PLATFORM_TENANT_ID} = {@code "*"}.
 */
@DisplayName("AdminOperator.isPlatformScope() 단위 테스트")
class AdminOperatorTest {

    @Test
    @DisplayName("tenantId='*' → isPlatformScope() returns true")
    void isPlatformScope_withStarSentinel_returnsTrue() {
        AdminOperator op = operator("*");
        assertThat(op.isPlatformScope()).isTrue();
    }

    @ParameterizedTest(name = "tenantId=''{0}'' → isPlatformScope() returns false")
    @ValueSource(strings = {"fan-platform", "global-admin", " ", "**", "SUPER"})
    @DisplayName("non-sentinel tenantId → isPlatformScope() returns false")
    void isPlatformScope_withNonSentinelTenantId_returnsFalse(String tenantId) {
        AdminOperator op = operator(tenantId);
        assertThat(op.isPlatformScope()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null or empty tenantId → isPlatformScope() returns false")
    void isPlatformScope_withNullOrEmpty_returnsFalse(String tenantId) {
        AdminOperator op = operator(tenantId);
        assertThat(op.isPlatformScope()).isFalse();
    }

    @Test
    @DisplayName("PLATFORM_TENANT_ID constant equals '*'")
    void platformTenantIdConstant_isStarString() {
        assertThat(AdminOperator.PLATFORM_TENANT_ID).isEqualTo("*");
    }

    @Test
    @DisplayName("ACTIVE operator isActive() returns true")
    void isActive_withActiveStatus_returnsTrue() {
        AdminOperator op = new AdminOperator("id-1", "a@ex.com", "Op",
                AdminOperator.Status.ACTIVE, 0L, "fan-platform");
        assertThat(op.isActive()).isTrue();
    }

    @Test
    @DisplayName("DISABLED operator isActive() returns false")
    void isActive_withDisabledStatus_returnsFalse() {
        AdminOperator op = new AdminOperator("id-1", "a@ex.com", "Op",
                AdminOperator.Status.DISABLED, 0L, "fan-platform");
        assertThat(op.isActive()).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static AdminOperator operator(String tenantId) {
        return new AdminOperator("id-1", "a@ex.com", "Op",
                AdminOperator.Status.ACTIVE, 0L, tenantId);
    }
}
