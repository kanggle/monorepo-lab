package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.port.VerifiedEmailMatchPort;
import com.example.admin.application.tenant.TenantSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-773 OD-7 — «받은 초대»: the list keys on the VERIFIED, normalised authoritative email only; every other
 * account state is an empty list with the state; no verdict propagates (503 — never «no invitations»).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ReceivedInvitationsQueryUseCaseTest {

    @Mock VerifiedEmailMatchPort matchPort;
    @Mock OperatorInvitationPort invitationPort;
    @Mock TenantProvisioningPort provisioningPort;

    private ReceivedInvitationsQueryUseCase useCase() {
        return new ReceivedInvitationsQueryUseCase(new OnboardingTrustGate(matchPort), invitationPort,
                provisioningPort);
    }

    private static Optional<OnboardingCallerAccount> account(String email) {
        return Optional.of(new OnboardingCallerAccount(email, "Caller"));
    }

    @Test
    @DisplayName("VERIFIED → live PENDING to the trimmed lower-cased email, limit 50; display name fail-soft; firstAdmin false (S4 adds it)")
    void verified_listsByNormalisedEmail() {
        when(matchPort.match("acc-1", " Recv@Example.COM ")).thenReturn(
                new VerifiedEmailMatchPort.MatchResult(VerifiedEmailMatchPort.Outcome.MATCHED, Instant.EPOCH));
        Instant exp = Instant.parse("2026-10-22T00:00:00Z");
        Instant at = Instant.parse("2026-10-15T00:00:00Z");
        when(invitationPort.findLivePendingByEmail(eq("recv@example.com"), any(), eq(50))).thenReturn(List.of(
                new OperatorInvitationPort.ReceivedInvitationView("inv-1", "acme", List.of("TENANT_ADMIN"), exp, at),
                new OperatorInvitationPort.ReceivedInvitationView("inv-2", "beta", List.of("SUPPORT_LOCK"), exp, at)));
        when(provisioningPort.get("acme")).thenReturn(
                new TenantSummary("acme", "Acme", "B2B_ENTERPRISE", "ACTIVE", Instant.EPOCH, Instant.EPOCH));
        when(provisioningPort.get("beta")).thenThrow(new RuntimeException("down"));

        ReceivedInvitationsQueryUseCase.Result r = useCase().read("acc-1", account(" Recv@Example.COM "));

        assertThat(r.accountState()).isEqualTo(OnboardingTrustGate.AccountState.VERIFIED);
        assertThat(r.items()).extracting(ReceivedInvitationsQueryUseCase.Item::invitationId)
                .containsExactly("inv-1", "inv-2");
        assertThat(r.items().get(0).tenantDisplayName()).isEqualTo("Acme");
        assertThat(r.items().get(1).tenantDisplayName()).isNull();
        assertThat(r.items()).allSatisfy(i -> assertThat(i.firstAdmin()).isFalse());
        assertThat(r.items().get(0).invitedAt()).isEqualTo(at);
    }

    @Test
    @DisplayName("EMAIL_NOT_VERIFIED · NOT_ELIGIBLE · MISMATCH → [] with the state, no invitation read")
    void otherStates_emptyList() {
        when(matchPort.match(eq("u"), any())).thenReturn(
                new VerifiedEmailMatchPort.MatchResult(VerifiedEmailMatchPort.Outcome.NOT_VERIFIED, null));
        when(matchPort.match(eq("s"), any())).thenReturn(
                new VerifiedEmailMatchPort.MatchResult(VerifiedEmailMatchPort.Outcome.NOT_ELIGIBLE, null));
        when(matchPort.match(eq("m"), any())).thenReturn(
                new VerifiedEmailMatchPort.MatchResult(VerifiedEmailMatchPort.Outcome.EMAIL_MISMATCH, null));

        assertThat(useCase().read("u", account("a@x.com")).accountState())
                .isEqualTo(OnboardingTrustGate.AccountState.EMAIL_NOT_VERIFIED);
        assertThat(useCase().read("s", account("a@x.com")).accountState())
                .isEqualTo(OnboardingTrustGate.AccountState.NOT_ELIGIBLE);
        ReceivedInvitationsQueryUseCase.Result m = useCase().read("m", account("a@x.com"));
        assertThat(m.accountState()).isEqualTo(OnboardingTrustGate.AccountState.NOT_ELIGIBLE);
        assertThat(m.items()).isEmpty();
        verifyNoInteractions(invitationPort, provisioningPort);
    }

    @Test
    @DisplayName("🔴 option A does not carry over: no account record (an iam-credential operator) → NOT_ELIGIBLE + [] · match not called")
    void noAccountRecord_notEligible() {
        ReceivedInvitationsQueryUseCase.Result r = useCase().read("acc-iam", Optional.empty());

        assertThat(r.accountState()).isEqualTo(OnboardingTrustGate.AccountState.NOT_ELIGIBLE);
        assertThat(r.items()).isEmpty();
        verifyNoInteractions(matchPort, invitationPort, provisioningPort);
    }

    @Test
    @DisplayName("no verdict → the downstream exception propagates (503), never an empty list")
    void noVerdict_propagates() {
        DownstreamFailureException down = new DownstreamFailureException("account-service unavailable");
        when(matchPort.match(eq("acc-1"), any())).thenThrow(down);

        assertThatThrownBy(() -> useCase().read("acc-1", account("a@x.com"))).isSameAs(down);
        verifyNoInteractions(invitationPort);
    }
}
