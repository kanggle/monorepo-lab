package com.example.auth.application;

import com.example.auth.application.exception.OperatorFacetUnavailableException;
import com.example.auth.application.port.OperatorFacetPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.SocialIdentityRepository;
import com.example.auth.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * TASK-BE-618 (ADR-MONO-078 A; auth-internal.md § POST /internal/auth/consumer-pool/moves) — the auth_db
 * half of moving a single-site consumer account into the consumer pool under the SAME account id.
 *
 * <p>account-service calls this as the LAST step of that account's account_db transaction; any answer but
 * {@link Outcome#MOVED} / {@link Outcome#ALREADY_IN_POOL} / {@link Outcome#NO_CREDENTIAL} makes it roll back.
 *
 * <p><b>Only {@code credentials.tenant_id} moves.</b> Deliberately untouched (task 착수 시 정정 ①②):
 * <ul>
 *   <li>{@code refresh_tokens} — the mirror row's tenant is the SESSION tenant (= the token's site,
 *       {@code AuthorizationSessionTenant}), which is already the target shape for a pool principal;
 *       moving it to {@code consumer-pool} would make {@code RefreshTokenUseCase} answer
 *       {@code TOKEN_TENANT_MISMATCH}.</li>
 *   <li>{@code social_identities} — an account that has any is refused ({@link Outcome#SOCIAL_LINKED})
 *       and handed to TASK-BE-617: the social lookup is keyed on the site tenant, so moving the row would
 *       break that person's social login.</li>
 * </ul>
 *
 * <p>Decision order (first match wins): already in pool → no credential → credential in an unexpected tenant
 * → social identities → a pool credential with the same email → operator facet (admin-service, fail-CLOSED)
 * → move. Every refusal writes nothing. Idempotent: a second call after a successful move answers
 * {@link Outcome#ALREADY_IN_POOL}, which is what completes the «auth committed, account rolled back» window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MoveCredentialToConsumerPoolUseCase {

    private final CredentialRepository credentialRepository;
    private final SocialIdentityRepository socialIdentityRepository;
    private final OperatorFacetPort operatorFacetPort;

    /** Every answer of the move; the controller maps each to its HTTP status / error code. */
    public enum Outcome {
        MOVED,
        ALREADY_IN_POOL,
        NO_CREDENTIAL,
        CREDENTIAL_TENANT_MISMATCH,
        SOCIAL_LINKED,
        POOL_CREDENTIAL_EXISTS,
        OPERATOR_FACETED,
        FACET_UNAVAILABLE
    }

    @Transactional
    public Outcome execute(String accountId, String siteTenantId) {
        Outcome outcome = decideAndMove(accountId, siteTenantId);
        // Account id + site + outcome only — never the email (confidential).
        log.info("consumer-pool credential move: account={} site={} outcome={}", accountId, siteTenantId, outcome);
        return outcome;
    }

    private Outcome decideAndMove(String accountId, String siteTenantId) {
        Optional<Credential> found = credentialRepository.findByAccountId(accountId);
        if (found.isEmpty()) {
            return Outcome.NO_CREDENTIAL;
        }
        Credential credential = found.get();
        if (TenantContext.isConsumerPool(credential.getTenantId())) {
            return Outcome.ALREADY_IN_POOL;
        }
        if (!credential.getTenantId().equals(siteTenantId)) {
            return Outcome.CREDENTIAL_TENANT_MISMATCH;
        }
        if (socialIdentityRepository.existsByAccountId(accountId)) {
            return Outcome.SOCIAL_LINKED;
        }
        Optional<Credential> poolTwin = credentialRepository.findPoolCredentialByEmail(credential.getEmail());
        if (poolTwin.isPresent() && !poolTwin.get().getAccountId().equals(accountId)) {
            return Outcome.POOL_CREDENTIAL_EXISTS;
        }
        String identityId = credentialRepository.findIdentityId(accountId).orElse(null);
        boolean faceted;
        try {
            faceted = operatorFacetPort.isOperatorFaceted(accountId, identityId);
        } catch (OperatorFacetUnavailableException e) {
            return Outcome.FACET_UNAVAILABLE;
        }
        if (faceted) {
            return Outcome.OPERATOR_FACETED;
        }
        int moved = credentialRepository.moveToTenant(
                accountId, siteTenantId, TenantContext.CONSUMER_POOL_TENANT_ID);
        if (moved != 1) {
            // The row changed tenant between the read and the guarded UPDATE — a concurrent move. Fail
            // rather than report a move that did not happen here; the caller rolls back and re-runs.
            throw new IllegalStateException("credential of account " + accountId + " was not in " + siteTenantId);
        }
        return Outcome.MOVED;
    }
}
