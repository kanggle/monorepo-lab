package com.example.auth.presentation;

import com.example.auth.application.BackfillCredentialIdentityUseCase;
import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.presentation.dto.BackfillCredentialIdentityRequest;
import com.example.auth.presentation.dto.BackfillCredentialIdentityResponse;
import com.example.auth.presentation.dto.CreateCredentialRequest;
import com.example.auth.presentation.dto.CreateCredentialResponse;
import com.example.auth.presentation.dto.ForceLogoutRequest;
import com.example.auth.presentation.dto.ForceLogoutResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
