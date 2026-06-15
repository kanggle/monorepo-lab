package com.example.auth.application;

import com.example.auth.domain.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): credential identity backfill — auth_db half of
 * the production reconciliation.
 *
 * <p>account-service owns the {@code account_id → identity_id} mapping (account_db) and
 * pushes it here in a batch; this use case writes each onto
 * {@code auth_db.credentials.identity_id} using the M2 writer
 * ({@link CredentialRepository#assignIdentityId}) — native, {@code IS NULL}-guarded,
 * idempotent, never overwriting (ADR-034 § 1.3). It is the bulk equivalent of the M2
 * in-band propagation for credentials that pre-date M2.</p>
 *
 * <p>net-zero / re-runnable: a pair whose credential is already linked (or absent)
 * assigns 0 rows. The whole batch is one transaction; the writer cannot violate any
 * constraint (it only sets a NULL column), so per-row failure is not a realistic mode.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillCredentialIdentityUseCase {

    private final CredentialRepository credentialRepository;

    public record Binding(String accountId, String identityId) {
    }

    public record Result(int requested, int updated) {
    }

    @Transactional
    public Result execute(List<Binding> bindings) {
        int updated = 0;
        for (Binding b : bindings) {
            if (b.identityId() == null || b.identityId().isBlank()
                    || b.accountId() == null || b.accountId().isBlank()) {
                continue;
            }
            updated += credentialRepository.assignIdentityId(b.accountId(), b.identityId());
        }
        log.info("[CredentialIdentityBackfill] requested={} updated={}", bindings.size(), updated);
        return new Result(bindings.size(), updated);
    }
}
