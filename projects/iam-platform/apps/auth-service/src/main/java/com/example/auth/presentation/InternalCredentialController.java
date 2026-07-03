package com.example.auth.presentation;

import com.example.auth.application.BackfillCredentialIdentityUseCase;
import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.ResolveCredentialAccountIdUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.presentation.dto.BackfillCredentialIdentityRequest;
import com.example.auth.presentation.dto.BackfillCredentialIdentityResponse;
import com.example.auth.presentation.dto.CreateCredentialRequest;
import com.example.auth.presentation.dto.CreateCredentialResponse;
import com.example.auth.presentation.dto.ForceLogoutRequest;
import com.example.auth.presentation.dto.ForceLogoutResponse;
import com.example.auth.presentation.dto.ResolveCredentialAccountIdRequest;
import com.example.auth.presentation.dto.ResolveCredentialAccountIdResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint for credential provisioning.
 *
 * <p>TASK-BE-063: account-service calls this after persisting a new Account so
 * auth_db.credentials has a row to authenticate against. Must never be exposed
 * through the public gateway (S2). See
 * {@code specs/contracts/http/internal/auth-internal.md}.</p>
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalCredentialController {

    private final CreateCredentialUseCase createCredentialUseCase;
    private final ForceLogoutUseCase forceLogoutUseCase;
    private final BackfillCredentialIdentityUseCase backfillCredentialIdentityUseCase;
    private final ResolveCredentialAccountIdUseCase resolveCredentialAccountIdUseCase;

    /**
     * Creates a credential row or returns success for an idempotent retry.
     *
     * <p>TASK-BE-247: returns 201 for a new row and 200 for an idempotent re-try of the same
     * (accountId, email) — enabling account-service to recover from half-commit scenarios where
     * auth-service committed after account-service timed out and rolled back.</p>
     */
    @PostMapping("/credentials")
    public ResponseEntity<CreateCredentialResponse> createCredential(
            @Valid @RequestBody CreateCredentialRequest request) {
        CreateCredentialResult result = createCredentialUseCase.execute(
                new CreateCredentialCommand(
                        request.accountId(),
                        request.email(),
                        request.password(),
                        request.tenantId(),   // TASK-BE-229: pass optional tenant context
                        request.identityId()  // TASK-BE-384 (ADR-036 M2): born-unified identity
                )
        );
        HttpStatus status = result.wasIdempotent() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(CreateCredentialResponse.from(result));
    }

    /**
     * TASK-BE-468 — the optional {@code X-Tenant-Id} header (stamped by admin-service
     * from the operator's active tenant, TASK-BE-467) confines the revoke to that
     * tenant: a concrete tenant that does not own the account → no-op (0 revoked).
     * Absent / {@code '*'} → net-zero (revoke across the account's tenant).
     */
    @PostMapping("/accounts/{accountId}/force-logout")
    public ResponseEntity<ForceLogoutResponse> forceLogout(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestBody(required = false) ForceLogoutRequest body) {
        ForceLogoutUseCase.Result result = forceLogoutUseCase.execute(accountId, tenantId);
        return ResponseEntity.ok(ForceLogoutResponse.from(result));
    }

    /**
     * TASK-BE-386 (ADR-MONO-036 P4, M4): bulk credential identity backfill — the auth_db
     * half of the production reconciliation. account-service posts the
     * {@code account_id → identity_id} pairs it resolved from account_db; each is written
     * onto {@code credentials.identity_id} via the M2 writer (idempotent, no overwrite).
     *
     * <p>A hyphen path (not an AIP-136 colon verb) is used deliberately: under the
     * class-level {@code /internal/auth} prefix, a {@code :verb} suffix mis-parses in
     * {@code PathPatternParser} (see {@code BulkAccountController}).</p>
     */
    @PostMapping("/credentials/identity-backfill")
    public ResponseEntity<BackfillCredentialIdentityResponse> backfillIdentity(
            @Valid @RequestBody BackfillCredentialIdentityRequest request) {
        List<BackfillCredentialIdentityUseCase.Binding> bindings = request.items().stream()
                .map(i -> new BackfillCredentialIdentityUseCase.Binding(i.accountId(), i.identityId()))
                .toList();
        BackfillCredentialIdentityUseCase.Result result = backfillCredentialIdentityUseCase.execute(bindings);
        return ResponseEntity.ok(
                new BackfillCredentialIdentityResponse(result.requested(), result.updated()));
    }

    /**
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — resolve a credential's
     * {@code account_id} from its login <b>email</b>. Backs the admin-service
     * one-time backfill that migrates {@code admin_operators.oidc_subject} from email
     * to account_id (the Phase-3 end-state key).
     *
     * <p><b>POST + body (NOT a path/query param)</b>: {@code email} is
     * {@code confidential} PII and must not land in the request URL / access logs
     * (the Phase-2 "no PII in query logs" discipline). The body also carries the
     * operator's {@code tenantId} because {@code credentials.email} is unique only
     * <b>per tenant</b> ({@code uk_credentials_tenant_email}, V0007) — a global
     * email lookup could mis-resolve to another tenant's account and mis-authorize
     * the operator.
     *
     * <p>Always HTTP 200 (fail-soft): a missing / ambiguous credential returns
     * {@code accountId=null} — the caller leaves that operator's {@code oidc_subject}
     * unchanged (it stays resolvable via the RETAINED email fallback). Under the
     * {@code /internal/**} chain (never exposed through the public gateway, S2).
     * Hyphen path (not an AIP-136 colon verb) — the {@code :verb} suffix mis-parses
     * under the class-level prefix in {@code PathPatternParser} (see
     * {@link #backfillIdentity}).
     */
    @PostMapping("/credentials/account-id-by-email")
    public ResponseEntity<ResolveCredentialAccountIdResponse> resolveAccountIdByEmail(
            @Valid @RequestBody ResolveCredentialAccountIdRequest request) {
        String accountId = resolveCredentialAccountIdUseCase
                .resolveAccountId(request.email(), request.tenantId())
                .orElse(null);
        return ResponseEntity.ok(new ResolveCredentialAccountIdResponse(accountId));
    }
}
