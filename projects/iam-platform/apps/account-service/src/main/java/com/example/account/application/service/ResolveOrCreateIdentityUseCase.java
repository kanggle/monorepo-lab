package com.example.account.application.service;

import com.example.account.domain.account.Email;
import com.example.account.domain.identity.Identity;
import com.example.account.domain.repository.IdentityRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d): resolve-or-create the central
 * {@code identities} row for a (tenant, email) — the provisioning primitive
 * unified new-operator creation calls (admin-service {@code CreateOperatorUseCase})
 * so every operator born after step 3 is linked to a central identity.
 *
 * <p><b>No silent merge (U3 / § 1.3).</b> The behavior is driven entirely by the
 * caller's explicit {@code reuseExisting} flag — an existing identity is REUSED
 * <em>only</em> when the caller opts in. Never merges and never mutates an
 * existing identity:
 *
 * <ul>
 *   <li>identity does NOT exist for (tenant, email) → CREATE a fresh
 *       {@link Identity#create(TenantId, String)} and return it → {@code CREATED}.</li>
 *   <li>exists AND {@code reuseExisting=true} → return the existing identity, no
 *       mutation → {@code REUSED}.</li>
 *   <li>exists AND {@code reuseExisting=false} → return {@code identityId=null}, NO
 *       mutation, NO merge → {@code EXISTS_NOT_REUSED} (the caller leaves the
 *       operator unlinked; explicit linking is the step-3c surface).</li>
 * </ul>
 *
 * <p><b>Race / idempotency.</b> On a fresh create, a concurrent insert may win the
 * {@code uk_identities_tenant_email} race; {@link DataIntegrityViolationException}
 * is caught and the row is re-read via
 * {@link IdentityRepository#findByTenantAndEmail(TenantId, String)}. The re-read
 * row is then returned as {@code REUSED} when the caller opted in, else
 * {@code EXISTS_NOT_REUSED} (still no merge) — so two concurrent provisioning
 * calls converge on the single registry row without a constraint-violation error
 * escaping.
 *
 * <p>Net effect: it IS a write (may create an identity), but writes NO audit row
 * (provisioning primitive) and NO outbox event — pure identity correlation (U5).
 * The email is validated + normalized (lowercase) by the {@link Email} value
 * object inside {@code Identity.create}; the lookup uses the same normalized form.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolveOrCreateIdentityUseCase {

    private final IdentityRepository identityRepository;

    @Transactional
    public ResolveOrCreateIdentityResult execute(String tenantId, String email, boolean reuseExisting) {
        TenantId tid = new TenantId(tenantId);
        // Normalize identically to Identity.create so the pre-check lookup and the
        // unique constraint agree on the key.
        String normalizedEmail = new Email(email).value();

        Optional<Identity> existing = identityRepository.findByTenantAndEmail(tid, normalizedEmail);
        if (existing.isPresent()) {
            return decideForExisting(existing.get(), reuseExisting);
        }

        // No existing identity → create a fresh one.
        try {
            Identity created = identityRepository.save(Identity.create(tid, normalizedEmail));
            return new ResolveOrCreateIdentityResult(created.getIdentityId(), Outcome.CREATED);
        } catch (DataIntegrityViolationException race) {
            // Concurrent insert won the uk_identities_tenant_email race. Re-read and
            // resolve per the caller's opt-in — NO merge, NO mutation.
            log.info("Concurrent identity insert race for (tenant={}, email=normalized); re-reading", tenantId);
            Identity raced = identityRepository.findByTenantAndEmail(tid, normalizedEmail)
                    .orElseThrow(() -> race);
            return decideForExisting(raced, reuseExisting);
        }
    }

    private ResolveOrCreateIdentityResult decideForExisting(Identity existing, boolean reuseExisting) {
        if (reuseExisting) {
            return new ResolveOrCreateIdentityResult(existing.getIdentityId(), Outcome.REUSED);
        }
        // Opt-in NOT given: no merge, no mutation, no identity handed back.
        return new ResolveOrCreateIdentityResult(null, Outcome.EXISTS_NOT_REUSED);
    }

    /** Outcome of a resolve-or-create call (no silent merge — ADR-034 U3). */
    public enum Outcome {
        /** A fresh identity was created for the (tenant, email). */
        CREATED,
        /** An existing identity was reused (caller opted in; no mutation). */
        REUSED,
        /** An identity exists but the caller did NOT opt in → no link, no merge. */
        EXISTS_NOT_REUSED
    }

    public record ResolveOrCreateIdentityResult(String identityId, Outcome outcome) {}
}
