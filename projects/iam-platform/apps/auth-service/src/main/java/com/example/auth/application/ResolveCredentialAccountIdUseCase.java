package com.example.auth.application;

import com.example.auth.domain.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — resolves an operator's
 * {@code account_id} from its login <b>email</b>, scoped by tenant. This is the
 * <b>reverse</b> direction of {@link ResolveCredentialEmailUseCase} (account_id →
 * email, Phase 2) and exists for the <b>one-time backfill</b> that migrates
 * {@code admin_operators.oidc_subject} from email to account_id (the Phase-3
 * end-state key), driven by the admin-service maintenance endpoint.
 *
 * <h2>Tenant scoping is MANDATORY (not advisory)</h2>
 *
 * <p>{@code auth_db.credentials} enforces a <b>composite</b> unique index
 * {@code uk_credentials_tenant_email (tenant_id, email)} (V0007 / TASK-BE-229) —
 * email is unique <b>per tenant</b>, NOT globally. The same email may therefore
 * map to <b>different</b> {@code account_id}s across tenants. Resolving the wrong
 * tenant's account would write a wrong {@code oidc_subject} and mis-authorize an
 * operator. The lookup is therefore keyed on {@code (tenantId, email)} via
 * {@link CredentialRepository#findByTenantIdAndEmail} — never a global email
 * lookup.
 *
 * <p>The operator's tenant_id is supplied by the caller (admin-service passes
 * {@code admin_operators.tenant_id}). For the SUPER_ADMIN sentinel
 * {@code tenant_id = '*'} the credential row is itself seeded under tenant
 * {@code '*'} (federation {@code seed.sql} § 2), so the composite lookup resolves
 * directly — an operator has exactly ONE account regardless of its assigned
 * tenants (the home/login tenant owns the credential).
 *
 * <h2>Cross-tenant collision detection (defence-in-depth)</h2>
 *
 * <p>When the supplied tenant resolves no credential, we do NOT silently fall back
 * to a global email lookup (that would re-introduce the mis-resolution risk). We
 * only consult {@link CredentialRepository#findAllByEmail} to <b>detect</b> whether
 * the email is unambiguous across tenants: if exactly ONE credential exists for the
 * email globally we may safely resolve it (the home tenant simply differs from the
 * operator row's tenant — e.g. a multi-assignment operator whose admin row tenant
 * was edited); if 2+ exist the email is ambiguous and we resolve to <b>empty</b>
 * (fail-soft — the operator stays on the retained email fallback rather than risk a
 * wrong account_id). This keeps a wrong account_id from ever being written.
 *
 * <p>Read-only. The email is {@code confidential} PII — never logged (only its
 * presence / the resolution outcome).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveCredentialAccountIdUseCase {

    private final CredentialRepository credentialRepository;

    /**
     * Resolve {@code email} (scoped by {@code tenantId}) to its credential
     * {@code account_id}.
     *
     * @param email    the operator's login email (raw input accepted; the
     *                 repository normalizes to trimmed lower-case)
     * @param tenantId the operator's tenant (composite-key scope). May be
     *                 {@code null}/blank — then only the global-unambiguity path is
     *                 consulted.
     * @return the resolved {@code account_id}, or empty when no credential matches
     *         the tenant scope AND the email is not globally unambiguous (fail-soft;
     *         a wrong account_id is never returned)
     */
    @Transactional(readOnly = true)
    public Optional<String> resolveAccountId(String email, String tenantId) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        // 1. Tenant-scoped lookup — the authoritative path (composite unique key).
        if (tenantId != null && !tenantId.isBlank()) {
            Optional<String> scoped = credentialRepository.findByTenantIdAndEmail(tenantId, email)
                    .map(c -> c.getAccountId());
            if (scoped.isPresent()) {
                log.debug("[ResolveCredentialAccountId] tenant-scoped lookup resolved a credential: present=true");
                return scoped;
            }
        }

        // 2. No tenant match → consult the cross-tenant set ONLY to decide whether
        //    the email is globally unambiguous. Never blindly pick one of several.
        List<String> accountIds = credentialRepository.findAllByEmail(email).stream()
                .map(c -> c.getAccountId())
                .distinct()
                .toList();
        if (accountIds.size() == 1) {
            log.debug("[ResolveCredentialAccountId] tenant miss but email globally unambiguous → resolved");
            return Optional.of(accountIds.get(0));
        }
        if (accountIds.size() > 1) {
            // Ambiguous across tenants — fail-soft (do NOT risk a wrong account_id).
            log.warn("[ResolveCredentialAccountId] email maps to {} accounts across tenants and "
                    + "no tenant-scoped match — resolving empty (operator stays on retained email "
                    + "fallback)", accountIds.size());
        } else {
            log.debug("[ResolveCredentialAccountId] no credential matches the email — empty");
        }
        return Optional.empty();
    }
}
