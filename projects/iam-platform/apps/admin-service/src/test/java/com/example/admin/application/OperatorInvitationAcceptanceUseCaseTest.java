package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationAccountNotEligibleException;
import com.example.admin.application.exception.OperatorInvitationAlreadyUsedException;
import com.example.admin.application.exception.OperatorInvitationEmailMismatchException;
import com.example.admin.application.exception.OperatorInvitationEmailNotVerifiedException;
import com.example.admin.application.exception.OperatorInvitationException;
import com.example.admin.application.exception.OperatorInvitationExpiredException;
import com.example.admin.application.exception.OperatorInvitationInvalidatedException;
import com.example.admin.application.exception.OperatorInvitationNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorIdentityResolvePort;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.port.VerifiedEmailMatchPort;
import com.example.admin.application.port.VerifiedEmailMatchPort.MatchResult;
import com.example.admin.application.port.VerifiedEmailMatchPort.Outcome;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-MONO-772 S3 — the acceptance use case (auth-to-admin.md § accept): the fail-closed order, «a refusal writes
 * nothing», the lost-race answers, OD-1 and the D3 re-check.
 *
 * <p>🔴 {@link ControlGroup} is AC-1's control group at the unit level — ONE invitation, the refusals asserted
 * first (unverified · other email · not a pool account · expired · cancelled · already used by someone else), each
 * proving the transaction was never entered, and then the verified owner's acceptance, which is the only call that
 * reaches the writer, with {@code accountId} as the new operator's {@code oidc_subject}. The DB-level twin is
 * {@code OperatorInvitationAcceptanceIntegrationTest}; the verified-email predicate itself lives in account-service
 * ({@code VerifiedEmailMatchUseCaseTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorInvitationAcceptanceUseCase — 운영자 초대 수락 (TASK-MONO-772 S3)")
class OperatorInvitationAcceptanceUseCaseTest {

    private static final String TOKEN = "raw-invitation-token-772";
    private static final String HASH = OperatorInvitationTokens.sha256Hex(TOKEN);
    private static final String INV = "0199de70-0000-7000-8000-0000000inv01";
    private static final String TENANT = "acme-corp";
    private static final String EMAIL = "person@example.com";
    private static final String INVITER = "op-admin-x";
    private static final long INVITER_ID = 11L;

    private static final String OWNER = "acc-owner-verified";
    private static final String UNVERIFIED = "acc-same-email-unverified";
    private static final String OTHER_EMAIL = "acc-other-email";
    private static final String SITE_ACCOUNT = "acc-site-account";

    @Mock OperatorInvitationPort invitationPort;
    @Mock OperatorInvitationAcceptanceWriter writer;
    @Mock VerifiedEmailMatchPort verifiedEmailMatchPort;
    @Mock TenantProvisioningPort provisioningPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock RoleGrantGuard roleGrantGuard;
    @Mock OperatorIdentityResolvePort identityResolvePort;

    private OperatorInvitationAcceptanceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new OperatorInvitationAcceptanceUseCase(invitationPort, writer, verifiedEmailMatchPort,
                provisioningPort, operatorPort, new OperatorOidcSubjectResolver(operatorPort), tenantScopeGuard,
                roleGrantGuard, identityResolvePort);
    }

    // ── fixtures ────────────────────────────────────────────────────────────

    private static InvitationView invitation(String status, Instant expiresAt, String acceptedAccountId,
                                             String acceptedOperatorId) {
        return new InvitationView(7L, INV, TENANT, EMAIL, "홍길동", List.of("SUPPORT_LOCK"), status, expiresAt,
                INVITER_ID, INVITER, "SENT", Instant.EPOCH, acceptedAccountId == null ? null : Instant.EPOCH,
                acceptedOperatorId, null, Instant.EPOCH, 0, HASH, acceptedAccountId);
    }

    private static InvitationView pending() {
        return invitation("PENDING", Instant.now().plus(Duration.ofDays(3)), null, null);
    }

    private static AdminOperatorPort.OperatorView operator(String operatorId, String tenantId, String status) {
        return new AdminOperatorPort.OperatorView(INVITER_ID, operatorId, tenantId, "admin@acme.example", null,
                "김관리", status, null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }

    private static Map<String, AdminOperatorPort.RoleView> supportLock() {
        Map<String, AdminOperatorPort.RoleView> m = new LinkedHashMap<>();
        m.put("SUPPORT_LOCK", new AdminOperatorPort.RoleView(3L, "SUPPORT_LOCK", "", false));
        return m;
    }

    private static TenantSummary tenant(String status) {
        return new TenantSummary(TENANT, "Acme Corp", "B2B_ENTERPRISE", status, Instant.EPOCH, Instant.EPOCH);
    }

    private void verdict(String accountId, Outcome outcome) {
        given(verifiedEmailMatchPort.match(accountId, EMAIL))
                .willReturn(new MatchResult(outcome, outcome == Outcome.MATCHED ? Instant.EPOCH : null));
    }

    /** Step 5 holds: tenant ACTIVE · inviter ACTIVE · in scope · every role grantable. */
    private void basisHolds() {
        given(provisioningPort.get(TENANT)).willReturn(tenant("ACTIVE"));
        given(operatorPort.findByOperatorId(INVITER)).willReturn(Optional.of(operator(INVITER, TENANT, "ACTIVE")));
        given(tenantScopeGuard.isTenantInScope(new OperatorContext(INVITER, null), Permission.OPERATOR_MANAGE, TENANT))
                .willReturn(true);
        given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
        given(roleGrantGuard.grantableRoleNames(eq(new OperatorContext(INVITER, null)), anyCollection()))
                .willReturn(List.of("SUPPORT_LOCK"));
    }

    private void assertRefused(Throwable thrown, Class<? extends OperatorInvitationException> type, String code,
                               int status) {
        assertThat(thrown).isInstanceOf(type);
        assertThat(((OperatorInvitationException) thrown).getCode()).isEqualTo(code);
        assertThat(((OperatorInvitationException) thrown).getHttpStatus()).isEqualTo(status);
    }

    // ── AC-1 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("🔴 AC-1 대조군 — 같은 초대: 거절 먼저, 인증된 본인만 성공")
    class ControlGroup {

        @Test
        @DisplayName("미인증 · 다른 이메일 · 풀 아님 · 만료 · 취소 · 남이 이미 수락 → 전부 거절, 트랜잭션 0회 · 인증된 본인 → 성공(oidc_subject = 계정)")
        void refusalsFirstThenOnlyTheVerifiedOwner() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));

            // ① the same email, not verified → 403 EMAIL_NOT_VERIFIED (770's shared name)
            verdict(UNVERIFIED, Outcome.NOT_VERIFIED);
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, UNVERIFIED)),
                    OperatorInvitationEmailNotVerifiedException.class, "EMAIL_NOT_VERIFIED", 403);

            // ② another address (verified or not — the mismatch answers first) → 403 EMAIL_MISMATCH
            verdict(OTHER_EMAIL, Outcome.EMAIL_MISMATCH);
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OTHER_EMAIL)),
                    OperatorInvitationEmailMismatchException.class, "OPERATOR_INVITATION_EMAIL_MISMATCH", 403);

            // ③ a site account (not the pool) → 403 ACCOUNT_NOT_ELIGIBLE
            verdict(SITE_ACCOUNT, Outcome.NOT_ELIGIBLE);
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, SITE_ACCOUNT)),
                    OperatorInvitationAccountNotEligibleException.class, "OPERATOR_INVITATION_ACCOUNT_NOT_ELIGIBLE", 403);

            // Nothing was written by any of the three: the transaction was never entered.
            verify(writer, never()).accept(any(), anyString(), anyCollection(), anyLong());

            // ④ the SAME invitation past its expiry — even for the verified owner → 410, and the owner is not asked.
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(
                    invitation("PENDING", Instant.now().minusSeconds(1), null, null)));
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationExpiredException.class, "OPERATOR_INVITATION_EXPIRED", 410);

            // ⑤ cancelled → 404 (not distinguished from «no such invitation»)
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(
                    invitation("CANCELLED", Instant.now().plus(Duration.ofDays(3)), null, null)));
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationNotFoundException.class, "OPERATOR_INVITATION_NOT_FOUND", 404);

            // ⑥ reused — already accepted by someone else → 409 ALREADY_USED
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(
                    invitation("ACCEPTED", Instant.now().plus(Duration.ofDays(3)), "acc-someone-else", "op-x")));
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationAlreadyUsedException.class, "OPERATOR_INVITATION_ALREADY_USED", 409);

            verify(writer, never()).accept(any(), anyString(), anyCollection(), anyLong());
            verify(verifiedEmailMatchPort, never()).match(eq(OWNER), anyString());

            // ⑦ the verified owner, the pending invitation → accepted; the writer gets THIS account as the subject.
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
            basisHolds();
            given(operatorPort.findByOidcSubject(OWNER)).willReturn(Optional.empty());
            given(operatorPort.existsByTenantIdAndEmail(TENANT, EMAIL)).willReturn(false);
            given(writer.accept(any(), eq(OWNER), anyCollection(), eq(INVITER_ID)))
                    .willReturn(new OperatorInvitationAcceptanceWriter.Accepted("op-new", 99L, "audit-1"));

            OperatorInvitationAcceptResult result = useCase.accept(TOKEN, OWNER);

            assertThat(result.operatorId()).isEqualTo("op-new");
            assertThat(result.tenantId()).isEqualTo(TENANT);
            assertThat(result.roles()).containsExactly("SUPPORT_LOCK");
            assertThat(result.alreadyAccepted()).isFalse();
            verify(writer).accept(any(), eq(OWNER), anyCollection(), eq(INVITER_ID));
        }
    }

    // ── order · lost races ──────────────────────────────────────────────────

    @Nested
    @DisplayName("판정 순서 · 경합")
    class OrderAndRaces {

        @Test
        @DisplayName("토큰에 맞는 초대 없음(재발송으로 죽은 옛 토큰 포함) → 404 · 아무것도 묻지 않는다")
        void unknownToken_404() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.empty());

            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationNotFoundException.class, "OPERATOR_INVITATION_NOT_FOUND", 404);
            verifyNoInteractions(verifiedEmailMatchPort, writer, provisioningPort);
        }

        @Test
        @DisplayName("같은 계정의 재제출 → 200 alreadyAccepted + 첫 결과 · 만료 시각이 지났어도(S1-2) · 다시 묻지 않는다")
        void sameAccountResubmit_returnsFirstResult_evenAfterExpiry() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(
                    invitation("ACCEPTED", Instant.now().minus(Duration.ofDays(1)), OWNER, "op-first")));

            OperatorInvitationAcceptResult result = useCase.accept(TOKEN, OWNER);

            assertThat(result.alreadyAccepted()).isTrue();
            assertThat(result.operatorId()).isEqualTo("op-first");
            assertThat(result.tenantId()).isEqualTo(TENANT);
            verifyNoInteractions(verifiedEmailMatchPort, writer);
        }

        @Test
        @DisplayName("판정을 못 받음(account-service 장애) → 그 예외 그대로(503) · fail-closed · 쓰기 없음")
        void noVerdict_failClosed() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            given(verifiedEmailMatchPort.match(OWNER, EMAIL))
                    .willThrow(new DownstreamFailureException("account-service unavailable", null));

            assertThatThrownBy(() -> useCase.accept(TOKEN, OWNER)).isInstanceOf(DownstreamFailureException.class);
            verifyNoInteractions(writer, provisioningPort);
        }

        @Test
        @DisplayName("🔴 OD-1: 이미 운영자 측면(상태 무관 — SUSPENDED 도) → 409 OPERATOR_ALREADY_PROVISIONED · 쓰기 없음")
        void alreadyProvisioned_409() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
            basisHolds();
            given(operatorPort.findByOidcSubject(OWNER))
                    .willReturn(Optional.of(operator("op-elsewhere", "other-corp", "SUSPENDED")));

            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorAlreadyProvisionedException.class, "OPERATOR_ALREADY_PROVISIONED", 409);
            verify(writer, never()).accept(any(), anyString(), anyCollection(), anyLong());
        }

        @Test
        @DisplayName("그 테넌트에 같은 이메일 운영자가 생겼다 → 409 OPERATOR_EMAIL_CONFLICT · 쓰기 없음")
        void emailConflict_409() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
            basisHolds();
            given(operatorPort.findByOidcSubject(OWNER)).willReturn(Optional.empty());
            given(operatorPort.existsByTenantIdAndEmail(TENANT, EMAIL)).willReturn(true);

            assertThatThrownBy(() -> useCase.accept(TOKEN, OWNER)).isInstanceOf(OperatorEmailConflictException.class);
            verify(writer, never()).accept(any(), anyString(), anyCollection(), anyLong());
        }

        @Test
        @DisplayName("동시 수락에 졌는데 이긴 쪽이 같은 계정(이중 제출) → 200 alreadyAccepted")
        void lostClaim_toSameAccount_isTheFirstResult() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
            basisHolds();
            given(operatorPort.findByOidcSubject(OWNER)).willReturn(Optional.empty());
            given(operatorPort.existsByTenantIdAndEmail(TENANT, EMAIL)).willReturn(false);
            given(writer.accept(any(), eq(OWNER), anyCollection(), eq(INVITER_ID)))
                    .willThrow(new OperatorInvitationAcceptanceWriter.ClaimLost());
            given(invitationPort.findByInvitationId(INV)).willReturn(Optional.of(
                    invitation("ACCEPTED", Instant.now().plus(Duration.ofDays(3)), OWNER, "op-winner")));

            OperatorInvitationAcceptResult result = useCase.accept(TOKEN, OWNER);

            assertThat(result.alreadyAccepted()).isTrue();
            assertThat(result.operatorId()).isEqualTo("op-winner");
        }

        @Test
        @DisplayName("동시 수락에 졌고 이긴 쪽이 다른 계정 → 409 ALREADY_USED · 재발송이 이겼다(해시 바뀜) → 404")
        void lostClaim_toOthers() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
            basisHolds();
            given(operatorPort.findByOidcSubject(OWNER)).willReturn(Optional.empty());
            given(operatorPort.existsByTenantIdAndEmail(TENANT, EMAIL)).willReturn(false);
            given(writer.accept(any(), eq(OWNER), anyCollection(), eq(INVITER_ID)))
                    .willThrow(new OperatorInvitationAcceptanceWriter.ClaimLost());
            given(invitationPort.findByInvitationId(INV)).willReturn(
                    Optional.of(invitation("ACCEPTED", Instant.now().plus(Duration.ofDays(3)), "acc-rival", "op-r")),
                    Optional.of(new InvitationView(7L, INV, TENANT, EMAIL, "홍길동", List.of("SUPPORT_LOCK"),
                            "PENDING", Instant.now().plus(Duration.ofDays(7)), INVITER_ID, INVITER, null, null,
                            null, null, null, Instant.EPOCH, 1, "b".repeat(64))));

            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationAlreadyUsedException.class, "OPERATOR_INVITATION_ALREADY_USED", 409);
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationNotFoundException.class, "OPERATOR_INVITATION_NOT_FOUND", 404);
        }

        @Test
        @DisplayName("oidc_subject UNIQUE 경합(다른 초대를 같은 계정이 동시에) → 다시 읽어 OD-1 409")
        void uniqueRace_mapsToAlreadyProvisioned() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
            basisHolds();
            given(operatorPort.findByOidcSubject(OWNER)).willReturn(
                    Optional.empty(), Optional.of(operator("op-race", "other-corp", "ACTIVE")));
            given(operatorPort.existsByTenantIdAndEmail(TENANT, EMAIL)).willReturn(false);
            given(writer.accept(any(), eq(OWNER), anyCollection(), eq(INVITER_ID)))
                    .willThrow(new DataIntegrityViolationException("uk_admin_operators_oidc_subject"));

            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorAlreadyProvisionedException.class, "OPERATOR_ALREADY_PROVISIONED", 409);
        }
    }

    // ── step 5 — the basis ──────────────────────────────────────────────────

    @Nested
    @DisplayName("근거 재판정(D-3 5 · S1-4) — 하나라도 깨지면 409 INVALIDATED · 쓰기 없음")
    class Basis {

        @BeforeEach
        void verifiedOwner() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            verdict(OWNER, Outcome.MATCHED);
        }

        private void assertInvalidated() {
            assertRefused(catchThrowable(() -> useCase.accept(TOKEN, OWNER)),
                    OperatorInvitationInvalidatedException.class, "OPERATOR_INVITATION_INVALIDATED", 409);
            verify(writer, never()).accept(any(), anyString(), anyCollection(), anyLong());
        }

        @Test
        @DisplayName("테넌트가 SUSPENDED")
        void tenantSuspended() {
            given(provisioningPort.get(TENANT)).willReturn(tenant("SUSPENDED"));
            assertInvalidated();
        }

        @Test
        @DisplayName("초대자가 더는 ACTIVE 가 아니다")
        void inviterSuspended() {
            given(provisioningPort.get(TENANT)).willReturn(tenant("ACTIVE"));
            given(operatorPort.findByOperatorId(INVITER)).willReturn(Optional.of(operator(INVITER, TENANT, "SUSPENDED")));
            assertInvalidated();
        }

        @Test
        @DisplayName("D2: 테넌트가 초대자의 관리 범위를 벗어났다")
        void outOfInviterScope() {
            given(provisioningPort.get(TENANT)).willReturn(tenant("ACTIVE"));
            given(operatorPort.findByOperatorId(INVITER)).willReturn(Optional.of(operator(INVITER, TENANT, "ACTIVE")));
            given(tenantScopeGuard.isTenantInScope(new OperatorContext(INVITER, null), Permission.OPERATOR_MANAGE, TENANT))
                    .willReturn(false);
            assertInvalidated();
        }

        @Test
        @DisplayName("🔴 D3: 7일 사이 초대자의 권한이 줄어 초대 역할이 부여 메뉴 밖 → 거절")
        void inviterPrivilegesShrank() {
            given(provisioningPort.get(TENANT)).willReturn(tenant("ACTIVE"));
            given(operatorPort.findByOperatorId(INVITER)).willReturn(Optional.of(operator(INVITER, TENANT, "ACTIVE")));
            given(tenantScopeGuard.isTenantInScope(new OperatorContext(INVITER, null), Permission.OPERATOR_MANAGE, TENANT))
                    .willReturn(true);
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            given(roleGrantGuard.grantableRoleNames(eq(new OperatorContext(INVITER, null)), anyCollection()))
                    .willReturn(List.of());
            assertInvalidated();
        }
    }

    // ── preview ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("preview — 읽기만")
    class Preview {

        @Test
        @DisplayName("회사 · 역할 · 마스킹 주소 · 상태 · 만료 — 원문 주소는 없다 · 쓰기·판정 호출 없음")
        void preview_shape() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(pending()));
            given(provisioningPort.get(TENANT)).willReturn(tenant("ACTIVE"));

            OperatorInvitationPreviewResult p = useCase.preview(TOKEN);

            assertThat(p.tenantId()).isEqualTo(TENANT);
            assertThat(p.tenantDisplayName()).isEqualTo("Acme Corp");
            assertThat(p.maskedEmail()).isEqualTo("p*****@example.com").doesNotContain("person");
            assertThat(p.roles()).containsExactly("SUPPORT_LOCK");
            assertThat(p.status()).isEqualTo("PENDING");
            assertThat(p.expired()).isFalse();
            verifyNoInteractions(writer, verifiedEmailMatchPort);
        }

        @Test
        @DisplayName("테넌트 읽기 실패 → tenantDisplayName null (fail-soft) · 만료된 초대도 200 으로 expired=true")
        void preview_failSoft_andExpired() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(
                    invitation("PENDING", Instant.now().minusSeconds(5), null, null)));
            given(provisioningPort.get(TENANT)).willThrow(new DownstreamFailureException("down", null));

            OperatorInvitationPreviewResult p = useCase.preview(TOKEN);

            assertThat(p.tenantDisplayName()).isNull();
            assertThat(p.expired()).isTrue();
        }

        @Test
        @DisplayName("취소된 초대 → 404 (없는 것과 구별 안 함)")
        void preview_cancelled_404() {
            given(invitationPort.findByTokenHash(HASH)).willReturn(Optional.of(
                    invitation("CANCELLED", Instant.now().plus(Duration.ofDays(1)), null, null)));

            assertRefused(catchThrowable(() -> useCase.preview(TOKEN)),
                    OperatorInvitationNotFoundException.class, "OPERATOR_INVITATION_NOT_FOUND", 404);
        }
    }

    @Test
    @DisplayName("token · accountId 누락 → 400 VALIDATION_ERROR(IllegalArgumentException) · 조회 없음")
    void missingInputs() {
        assertThatThrownBy(() -> useCase.accept(" ", OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.accept(TOKEN, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.preview(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(invitationPort);
    }
}
