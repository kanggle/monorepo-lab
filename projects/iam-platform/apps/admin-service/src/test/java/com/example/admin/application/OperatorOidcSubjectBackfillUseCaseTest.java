package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.AdminOperatorPort.OperatorOidcSubjectView;
import com.example.admin.infrastructure.client.AuthServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — unit tests for the idempotent
 * {@link OperatorOidcSubjectBackfillUseCase}: email-shaped → account_id migration,
 * UUID-shaped/null skip (idempotency), unresolved fail-soft (left unchanged +
 * counted), tenant-scoped account_id resolution, report counts, and PII-safe audit
 * (the use case never receives or logs the email value — it logs only the key-shape).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorOidcSubjectBackfillUseCase (idempotent backfill) 단위 테스트")
class OperatorOidcSubjectBackfillUseCaseTest {

    private static final Instant FIXED = Instant.parse("2026-06-18T00:00:00Z");
    private static final String ACME_ACCOUNT = "01928c4a-7e9f-7c00-9a40-d2b1f5e8c200";

    @Mock AdminOperatorPort operatorPort;
    @Mock AuthServiceClient authServiceClient;

    private OperatorOidcSubjectBackfillUseCase useCase() {
        return new OperatorOidcSubjectBackfillUseCase(
                operatorPort, authServiceClient, Clock.fixed(FIXED, ZoneOffset.UTC));
    }

    private OperatorOidcSubjectView op(String operatorId, String tenant, String oidcSubject) {
        return new OperatorOidcSubjectView(operatorId.hashCode(), operatorId, tenant, oidcSubject);
    }

    @Test
    @DisplayName("email-shaped row → account_id 로 update + report.updated=1")
    void emailShaped_updatedToAccountId() {
        OperatorOidcSubjectView row = op("acme-corp-operator", "acme-corp", "acme-operator@example.com");
        when(operatorPort.findOperatorsWithOidcSubject()).thenReturn(List.of(row));
        when(authServiceClient.resolveOperatorAccountId("acme-operator@example.com", "acme-corp"))
                .thenReturn(Optional.of(ACME_ACCOUNT));

        var report = useCase().run();

        verify(operatorPort).updateOidcSubject(row.internalId(), ACME_ACCOUNT, FIXED);
        assertThat(report.scanned()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.skippedAlreadyUuid()).isZero();
        assertThat(report.unresolved()).isZero();
    }

    @Test
    @DisplayName("이미 UUID-shaped row → skip (idempotent re-run no-op), auth 미조회")
    void alreadyUuid_skipped_idempotent() {
        OperatorOidcSubjectView row = op("acme-corp-operator", "acme-corp", ACME_ACCOUNT);
        when(operatorPort.findOperatorsWithOidcSubject()).thenReturn(List.of(row));

        var report = useCase().run();

        verify(operatorPort, never()).updateOidcSubject(anyLong(), anyString(), eq(FIXED));
        verifyNoInteractions(authServiceClient);
        assertThat(report.scanned()).isEqualTo(1);
        assertThat(report.updated()).isZero();
        assertThat(report.skippedAlreadyUuid()).isEqualTo(1);
    }

    @Test
    @DisplayName("unresolved (auth 가 empty) → 변경 없이 unresolved 카운트 (fail-soft, retained fallback)")
    void unresolved_leftUnchanged_counted() {
        OperatorOidcSubjectView row = op("ghost-operator", "ghost-corp", "ghost@example.com");
        when(operatorPort.findOperatorsWithOidcSubject()).thenReturn(List.of(row));
        when(authServiceClient.resolveOperatorAccountId("ghost@example.com", "ghost-corp"))
                .thenReturn(Optional.empty());

        var report = useCase().run();

        verify(operatorPort, never()).updateOidcSubject(anyLong(), anyString(), eq(FIXED));
        assertThat(report.scanned()).isEqualTo(1);
        assertThat(report.updated()).isZero();
        assertThat(report.unresolved()).isEqualTo(1);
    }

    @Test
    @DisplayName("혼합 배치: email-shaped(해석/미해석) + UUID-shaped → 정확한 report 카운트, 한 건 미해석이 배치 중단 안 함")
    void mixedBatch_correctCounts() {
        OperatorOidcSubjectView toUpdate = op("acme-corp-operator", "acme-corp", "acme-operator@example.com");
        OperatorOidcSubjectView alreadyDone = op("multi-operator", "acme-corp", ACME_ACCOUNT);
        OperatorOidcSubjectView unresolvable = op("ghost", "ghost-corp", "ghost@example.com");
        when(operatorPort.findOperatorsWithOidcSubject())
                .thenReturn(List.of(toUpdate, alreadyDone, unresolvable));
        when(authServiceClient.resolveOperatorAccountId("acme-operator@example.com", "acme-corp"))
                .thenReturn(Optional.of(ACME_ACCOUNT));
        when(authServiceClient.resolveOperatorAccountId("ghost@example.com", "ghost-corp"))
                .thenReturn(Optional.empty());

        var report = useCase().run();

        verify(operatorPort).updateOidcSubject(toUpdate.internalId(), ACME_ACCOUNT, FIXED);
        verify(operatorPort, never()).updateOidcSubject(eq(alreadyDone.internalId()), anyString(), eq(FIXED));
        verify(operatorPort, never()).updateOidcSubject(eq(unresolvable.internalId()), anyString(), eq(FIXED));
        assertThat(report.scanned()).isEqualTo(3);
        assertThat(report.updated()).isEqualTo(1);
        assertThat(report.skippedAlreadyUuid()).isEqualTo(1);
        assertThat(report.unresolved()).isEqualTo(1);
    }

    @Test
    @DisplayName("provisioned operator 없음 → 빈 report")
    void noOperators_emptyReport() {
        when(operatorPort.findOperatorsWithOidcSubject()).thenReturn(List.of());

        var report = useCase().run();

        assertThat(report.scanned()).isZero();
        assertThat(report.updated()).isZero();
        verifyNoInteractions(authServiceClient);
    }

    @Test
    @DisplayName("tenant scoping: 운영자의 tenant_id 가 auth 조회로 전달된다")
    void tenantScopingPassedThrough() {
        OperatorOidcSubjectView row = op("umbrella-admin", "umbrella-corp", "umbrella@example.com");
        when(operatorPort.findOperatorsWithOidcSubject()).thenReturn(List.of(row));
        when(authServiceClient.resolveOperatorAccountId("umbrella@example.com", "umbrella-corp"))
                .thenReturn(Optional.of("01928c4a-7e9f-7c00-9a40-d2b1f5e8c401"));

        useCase().run();

        verify(authServiceClient).resolveOperatorAccountId("umbrella@example.com", "umbrella-corp");
    }

    // ── email-shape detection (the idempotency filter) ──────────────────────

    @Test
    @DisplayName("isEmailShaped: @ 포함 + UUID 아님 → true; UUID → false; @ 없음 → false; null/blank → false")
    void isEmailShaped_rules() {
        assertThat(OperatorOidcSubjectBackfillUseCase.isEmailShaped("op@example.com")).isTrue();
        assertThat(OperatorOidcSubjectBackfillUseCase.isEmailShaped(ACME_ACCOUNT)).isFalse();
        assertThat(OperatorOidcSubjectBackfillUseCase.isEmailShaped("e2e-target-oidc-sub")).isFalse();
        assertThat(OperatorOidcSubjectBackfillUseCase.isEmailShaped(null)).isFalse();
        assertThat(OperatorOidcSubjectBackfillUseCase.isEmailShaped("  ")).isFalse();
    }
}
