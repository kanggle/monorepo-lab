package com.example.auth.application;

import com.example.auth.domain.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * TASK-MONO-295 (ADR-MONO-040 Phase 2) — resolves an account's login email from
 * the validated {@code account_id} (= the SAS access-token {@code sub}).
 *
 * <p>This is the auth_db.credentials side of the operator-token-exchange DUAL-KEY
 * migration. Phase 2 flips the SAS access-token {@code sub} to the account UUID,
 * but {@code admin_operators.oidc_subject} is still seeded with the operator's
 * login <b>email</b> (federation {@code seed.sql}). The login-time operator-token
 * exchange ({@code POST /api/admin/auth/token-exchange}, admin-service
 * {@code TokenExchangeService}) is reached <b>directly</b> by console-web — it does
 * NOT pass through auth-service — so admin-service cannot read
 * {@code auth_db.credentials} locally the way
 * {@code AssumeTenantAuthenticationProvider} does. admin-service resolves the
 * legacy email fallback key by calling the internal endpoint backed by this use
 * case, against the <b>same</b> {@code CredentialRepository.findByAccountId} source
 * the assume-tenant path uses server-side (single source of truth for
 * account_id → email; no PII on any token).
 *
 * <p>The email is {@code confidential} PII (data-model.md
 * {@code admin_operators.email}); the caller carries it off logged URLs and never
 * logs it. A missing credential row (non-SAS / legacy / break-glass subject) yields
 * an empty result → the caller proceeds on account_id-only resolution (graceful).
 *
 * <p>No write, read-only. {@code @Transactional(readOnly = true)} for the
 * single-row lookup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveCredentialEmailUseCase {

    private final CredentialRepository credentialRepository;

    /**
     * @param accountId the credential's {@code account_id} (= the validated SAS
     *                  access-token {@code sub})
     * @return the credential's login email, or empty when no credential row exists
     *         for {@code accountId} (graceful — caller resolves on account_id alone)
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveEmail(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        // The email is NEVER logged here (PII) — only its presence.
        Optional<String> email = credentialRepository.findByAccountId(accountId)
                .map(com.example.auth.domain.credentials.Credential::getEmail);
        log.debug("[ResolveCredentialEmail] account_id lookup resolved a credential email: {}",
                email.isPresent());
        return email;
    }
}
