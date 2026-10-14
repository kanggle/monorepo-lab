package com.example.account.domain.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-773 S2 — {@link TenantStatus#valueOf(String) PENDING} on the {@link Tenant} aggregate.
 * Contract: {@code admin-to-account.md} § «Tenant lifecycle — 773 변경분» (전이표).
 *
 * <p>The value is reached through {@link #pending()} (name lookup, behind an assertion) rather than
 * a constant, so this class compiles before the value exists and fails at that assertion.
 */
@DisplayName("TASK-MONO-773 S2 — Tenant PENDING 상태 · 전이")
class TenantPendingStatusTest {

    private static final Instant T0 = Instant.parse("2026-10-15T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-15T00:01:00Z");

    private static TenantStatus pending() {
        assertThat(Arrays.stream(TenantStatus.values()).map(Enum::name))
                .as("TenantStatus carries PENDING (첫 관리자 대기 — TASK-MONO-773 OD-1)")
                .contains("PENDING");
        return TenantStatus.valueOf("PENDING");
    }

    private static Tenant tenantIn(TenantStatus status) {
        return Tenant.reconstitute(new TenantId("acme-new"), "Acme New", TenantType.B2B_ENTERPRISE,
                status, T0, T0);
    }

    @Test
    @DisplayName("PENDING 은 «활성 아님» — isActive() 술어 무변경으로 가입·프로비저닝이 닫힌다")
    void pendingIsNotActive() {
        assertThat(tenantIn(pending()).isActive()).isFalse();
    }

    @Test
    @DisplayName("PENDING → ACTIVE 허용 (변경 true · updatedAt 갱신)")
    void pendingToActive() {
        Tenant tenant = tenantIn(pending());

        assertThat(tenant.updateStatus(TenantStatus.ACTIVE, T1)).isTrue();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.getUpdatedAt()).isEqualTo(T1);
        assertThat(tenant.isActive()).isTrue();
    }

    @Test
    @DisplayName("PENDING → SUSPENDED 허용 (정리 · 생성 보상)")
    void pendingToSuspended() {
        Tenant tenant = tenantIn(pending());

        assertThat(tenant.updateStatus(TenantStatus.SUSPENDED, T1)).isTrue();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }

    @Test
    @DisplayName("→ PENDING 은 요청 값이 될 수 없다 — ACTIVE · SUSPENDED · PENDING 어디서든 거절, 상태 불변")
    void toPendingIsRejectedFromEveryState() {
        TenantStatus pending = pending();
        for (TenantStatus from : TenantStatus.values()) {
            Tenant tenant = tenantIn(from);

            assertThatThrownBy(() -> tenant.updateStatus(pending, T1))
                    .as("updateStatus(PENDING) from %s", from)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(tenant.getStatus()).isEqualTo(from);
            assertThat(tenant.getUpdatedAt()).isEqualTo(T0);
        }
    }

    @Test
    @DisplayName("대조군 — 기존 생성 팩토리는 지금처럼 ACTIVE")
    void legacyCreateStaysActive() {
        Tenant tenant = Tenant.create(new TenantId("acme-new"), "Acme New", TenantType.B2B_ENTERPRISE, T0);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }
}
