package com.example.account.presentation.internal;

import com.example.account.application.service.CredentialIdentityBackfillUseCase;
import com.example.account.presentation.dto.response.IdentityBackfillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): admin-triggered production data reconciliation —
 * propagates the central {@code identity_id} from account_db to {@code auth_db.credentials}.
 *
 * <p>URL: {@code POST /internal/identity-backfill/credentials}
 * <p>Authentication: {@code /internal/**} — GAP {@code client_credentials} Bearer JWT in
 * real profiles (the dev/test bypass authenticates slice tests), per {@code InternalApiFilter}.
 *
 * <p>Idempotent and re-runnable: it only sets a previously-NULL {@code credentials.identity_id}
 * (the auth-service writer guards on {@code IS NULL}); a second run updates 0 rows. The
 * account_db half (orphan identity mint+link) is the V0024 Flyway migration; the admin_db
 * operator half stays the opt-in audited link surface. See the ADR-036 P4 runbook.
 */
@RestController
@RequiredArgsConstructor
public class IdentityBackfillController {

    private final CredentialIdentityBackfillUseCase credentialIdentityBackfillUseCase;

    @PostMapping("/internal/identity-backfill/credentials")
    public ResponseEntity<IdentityBackfillResponse> backfillCredentials() {
        CredentialIdentityBackfillUseCase.Result result = credentialIdentityBackfillUseCase.execute();
        return ResponseEntity.ok(IdentityBackfillResponse.from(result));
    }
}
