package com.example.admin.application;

import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.tenant.TenantSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * TASK-MONO-773 S3 (owner decisions OD-7 · S1 Q6 — onboarding-api.md § POST …/received-invitations) — «받은 초대»:
 * the operator invitations (TASK-MONO-772) addressed to the caller's VERIFIED email. 🔴 Read-only: no write, no
 * audit row, no event, no token / link / inviter in the answer (772 R4 — the mailbox stays the proof; acceptance is
 * the email link at the IdP, unchanged).
 *
 * <ul>
 *   <li>{@code accountState} = {@link OnboardingTrustGate#accountState} — the same call and mapping as the
 *       onboarding gate; a caller with no account record (an {@code iam}-credential operator included) is
 *       {@code NOT_ELIGIBLE}: there is no verified email to key the list on.</li>
 *   <li>items only when {@code VERIFIED}: PENDING · not expired · {@code email} = the account's email (trim ·
 *       lower-case) · newest first · at most {@value #LIMIT}. The input is the token's {@code sub} only — no address
 *       is ever taken from the request.</li>
 *   <li>No verdict → the downstream exception propagates ({@code 503}) — never an empty list («none» and «unknown»
 *       are different answers).</li>
 *   <li>{@code firstAdmin} is {@code false} until S4 adds {@code operator_invitation.first_admin}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceivedInvitationsQueryUseCase {

    static final int LIMIT = 50;

    private final OnboardingTrustGate trustGate;
    private final OperatorInvitationPort invitationPort;
    private final TenantProvisioningPort provisioningPort;

    public Result read(String accountId, Optional<OnboardingCallerAccount> account) {
        OnboardingTrustGate.AccountState state = trustGate.accountState(accountId, account);
        if (state != OnboardingTrustGate.AccountState.VERIFIED) {
            return new Result(state, List.of());
        }
        String email = account.orElseThrow().email().trim().toLowerCase(Locale.ROOT);
        List<Item> items = new ArrayList<>();
        for (OperatorInvitationPort.ReceivedInvitationView v
                : invitationPort.findLivePendingByEmail(email, Instant.now(), LIMIT)) {
            items.add(new Item(v.invitationId(), v.tenantId(), tenantDisplayName(v.tenantId()), v.roles(),
                    false, v.expiresAt(), v.invitedAt()));
        }
        return new Result(state, List.copyOf(items));
    }

    private String tenantDisplayName(String tenantId) {
        try {
            TenantSummary tenant = provisioningPort.get(tenantId);
            return tenant == null ? null : tenant.displayName();
        } catch (RuntimeException e) {
            // Fail-soft (772 preview rule): a picture, not a verdict — the screen falls back to the tenant id.
            log.info("received-invitations: tenant display name unavailable (tenant={}): {}",
                    tenantId, e.getClass().getSimpleName());
            return null;
        }
    }

    public record Result(OnboardingTrustGate.AccountState accountState, List<Item> items) {}

    public record Item(String invitationId, String tenantId, String tenantDisplayName, List<String> roles,
                       boolean firstAdmin, Instant expiresAt, Instant invitedAt) {}
}
