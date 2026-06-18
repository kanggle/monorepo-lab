package com.example.auth.presentation;

import com.example.auth.application.BackfillCredentialIdentityUseCase;
import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.ResolveCredentialEmailUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.presentation.dto.BackfillCredentialIdentityRequest;
import com.example.auth.presentation.dto.BackfillCredentialIdentityResponse;
import com.example.auth.presentation.dto.CreateCredentialRequest;
import com.example.auth.presentation.dto.CreateCredentialResponse;
import com.example.auth.presentation.dto.ForceLogoutRequest;
import com.example.auth.presentation.dto.ForceLogoutResponse;
import com.example.auth.presentation.dto.ResolveCredentialEmailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ResolveCredentialEmailUseCase resolveCredentialEmailUseCase;

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

    @PostMapping("/accounts/{accountId}/force-logout")
    public ResponseEntity<ForceLogoutResponse> forceLogout(
            @PathVariable String accountId,
            @RequestBody(required = false) ForceLogoutRequest body) {
        ForceLogoutUseCase.Result result = forceLogoutUseCase.execute(accountId);
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
     * TASK-MONO-295 (ADR-MONO-040 Phase 2) — resolve a credential's login email
     * from its {@code account_id} (= the SAS access-token {@code sub}).
     *
     * <p>The login-time operator-token exchange (admin-service
     * {@code TokenExchangeService}, {@code POST /api/admin/auth/token-exchange}) is
     * reached <b>directly</b> by console-web and so cannot read
     * {@code auth_db.credentials} the way the assume-tenant provider does
     * server-side. It calls this read-only endpoint to obtain the legacy
     * DUAL-KEY email fallback (the value {@code admin_operators.oidc_subject} is
     * currently seeded with) against the <b>same</b>
     * {@code CredentialRepository.findByAccountId} source — single source of truth
     * for account_id → email, no PII on any token.
     *
     * <p>Always 200: a missing credential row returns {@code email=null} (the
     * caller then resolves the operator on account_id alone — graceful). The email
     * is {@code confidential} PII; the caller keeps it off logged URLs (passes it as
     * a header to admin-service, not a query param) and never logs it. Under the
     * {@code /internal/**} chain (never exposed through the public gateway, S2).
     */
    @GetMapping("/credentials/{accountId}/email")
    public ResponseEntity<ResolveCredentialEmailResponse> resolveEmail(
            @PathVariable String accountId) {
        String email = resolveCredentialEmailUseCase.resolveEmail(accountId).orElse(null);
        return ResponseEntity.ok(new ResolveCredentialEmailResponse(accountId, email));
    }
}
