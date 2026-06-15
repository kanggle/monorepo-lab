package com.example.account.application.service;

import com.example.account.application.port.AccountIdentityBindingReader;
import com.example.account.application.port.AuthServicePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): production data reconciliation driver — propagates
 * the central {@code identity_id} from account_db to {@code auth_db.credentials}.
 *
 * <p>The account_db half (mint + link orphan accounts) is the V0024 Flyway migration.
 * The auth_db half cannot be a migration (physically separate database), so this
 * account-service-driven step reads the resolved {@code account_id → identity_id}
 * bindings (cross-tenant, via {@link AccountIdentityBindingReader}) and pushes them to
 * auth-service, which writes them with the idempotent, no-overwrite M2 writer.</p>
 *
 * <p><b>Safety</b>: same-origin propagation (the identity each account already
 * resolved to), NOT an email merge; idempotent end-to-end (re-running assigns 0 rows
 * once linked); additive (only sets a NULL {@code credentials.identity_id}). The admin_db
 * operator half is intentionally NOT touched here — operator↔consumer linking stays the
 * opt-in audited link surface (ADR-034 § 1.3 privilege-escalation guard).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialIdentityBackfillUseCase {

    private final AccountIdentityBindingReader bindingReader;
    private final AuthServicePort authServicePort;

    public record Result(int accountsScanned, int credentialsUpdated) {
    }

    public Result execute() {
        List<AuthServicePort.CredentialIdentityBinding> bindings = bindingReader.findLinkedBindings();
        if (bindings.isEmpty()) {
            log.info("[CredentialIdentityBackfill] no linked accounts to propagate — net-zero");
            return new Result(0, 0);
        }
        int updated = authServicePort.backfillCredentialIdentities(bindings);
        log.info("[CredentialIdentityBackfill] propagated {} bindings to auth — {} credentials updated",
                bindings.size(), updated);
        return new Result(bindings.size(), updated);
    }
}
