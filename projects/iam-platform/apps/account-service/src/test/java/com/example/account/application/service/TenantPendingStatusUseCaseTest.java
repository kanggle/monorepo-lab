package com.example.account.application.service;

import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.result.TenantResult;
import com.example.account.domain.repository.PageResult;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-773 S2 — application-layer behaviour of the {@code PENDING} tenant status.
 * Contract: {@code admin-to-account.md} § «Tenant lifecycle — 773 변경분».
 *
 * <p>{@code PENDING} is reached by name behind an assertion ({@link #pending()}) so the class
 * compiles before the value exists.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TASK-MONO-773 S2 — PENDING 테넌트: 가드 · 수명주기 유스케이스")
class TenantPendingStatusUseCaseTest {

    private static final String TENANT = "acme-new";
    private static final Instant T0 = Instant.parse("2026-10-15T00:00:00Z");

    @Mock private TenantRepository tenantRepository;
    @InjectMocks private TenantProvisionUseCase tenantProvisionUseCase;

    private static TenantStatus pending() {
        assertThat(Arrays.stream(TenantStatus.values()).map(Enum::name))
                .as("TenantStatus carries PENDING (첫 관리자 대기 — TASK-MONO-773 OD-1)")
                .contains("PENDING");
        return TenantStatus.valueOf("PENDING");
    }

    private static Tenant tenantIn(TenantStatus status) {
        return Tenant.reconstitute(new TenantId(TENANT), "Acme New", TenantType.B2B_ENTERPRISE,
                status, T0, T0);
    }

    // ── ActiveTenantGuard (signup · social signup) ──────────────────────────────

    @Test
    @DisplayName("🔴 ActiveTenantGuard — PENDING 테넌트 → TenantSuspendedException (새 코드 없음)")
    void guard_refusesPendingTenant() {
        TenantStatus pending = pending();
        given(tenantRepository.findById(new TenantId(TENANT))).willReturn(Optional.of(tenantIn(pending)));
        ActiveTenantGuard guard = new ActiveTenantGuard(tenantRepository);

        assertThatThrownBy(() -> guard.requireActive(new TenantId(TENANT)))
                .isInstanceOf(TenantSuspendedException.class);
    }

    // ── TenantProvisionUseCase.update ────────────────────────────────────────────

    @Test
    @DisplayName("update PENDING → ACTIVE — 저장되는 값이 ACTIVE")
    void update_pendingToActive_saves() {
        TenantStatus pending = pending();
        given(tenantRepository.findById(new TenantId(TENANT))).willReturn(Optional.of(tenantIn(pending)));
        given(tenantRepository.save(any(Tenant.class))).willAnswer(inv -> inv.getArgument(0));

        TenantResult result = tenantProvisionUseCase.update(TENANT, null, "ACTIVE");

        ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("update PENDING → SUSPENDED — 저장되는 값이 SUSPENDED")
    void update_pendingToSuspended_saves() {
        TenantStatus pending = pending();
        given(tenantRepository.findById(new TenantId(TENANT))).willReturn(Optional.of(tenantIn(pending)));
        given(tenantRepository.save(any(Tenant.class))).willAnswer(inv -> inv.getArgument(0));

        TenantResult result = tenantProvisionUseCase.update(TENANT, null, "SUSPENDED");

        assertThat(result.status()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("update status=PENDING (PENDING 테넌트에서도) → IllegalArgumentException(400) · 저장 0")
    void update_toPending_fromPending_isRejected() {
        TenantStatus pending = pending();
        given(tenantRepository.findById(new TenantId(TENANT))).willReturn(Optional.of(tenantIn(pending)));

        assertThatThrownBy(() -> tenantProvisionUseCase.update(TENANT, null, "PENDING"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tenantRepository, never()).save(any());
    }

    // ── TenantProvisionUseCase.list ─────────────────────────────────────────────

    @Test
    @DisplayName("list(status=PENDING) — 저장소에 PENDING 필터를 넘긴다")
    void list_pendingFilter_isPassedThrough() {
        TenantStatus pending = pending();
        given(tenantRepository.findAll(eq(pending), eq(null), eq(0), eq(20)))
                .willReturn(new PageResult<>(List.of(tenantIn(pending)), 1L, 0, 20, 1));

        var page = tenantProvisionUseCase.list("PENDING", null, 0, 20);

        assertThat(page.items()).extracting(TenantResult::status).containsExactly("PENDING");
    }
}
