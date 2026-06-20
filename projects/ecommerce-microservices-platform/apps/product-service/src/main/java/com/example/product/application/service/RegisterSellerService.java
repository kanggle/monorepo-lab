package com.example.product.application.service;

import com.example.product.application.command.RegisterSellerCommand;
import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.product.domain.model.Seller;
import com.example.product.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Seller onboarding + lifecycle use case (ADR-MONO-030 Step 3 §3.1 + Step 4 facet f,
 * ADR-MONO-042). Onboarding now mints a REAL IAM seller-operator account (fail-soft, D3)
 * instead of leaving the seller a trusted-claim shim:
 *
 * <ul>
 *   <li>{@link #register} — create the seller {@code PENDING_PROVISIONING}, attempt IAM
 *       provisioning, transition to {@code ACTIVE} on success; on IAM unavailability the
 *       seller STAYS {@code PENDING_PROVISIONING} (logged, retryable) — onboarding never
 *       blocks (D3 fail-soft).</li>
 *   <li>{@link #provisionPending} — the retry trigger for a PENDING seller (re-provision),
 *       and the reconciliation top-up for a null {@code identity_id} on an ACTIVE seller
 *       (m2 — matches the contract "filled on re-provision").</li>
 *   <li>{@link #suspend} / {@link #close} — operator deactivation; locks/deactivates the
 *       backing account (D4, idempotent + null-safe / net-zero).</li>
 *   <li>{@link #ensureDefaultSeller} — the per-tenant default seller (D8 anchor), born
 *       ACTIVE and never IAM-provisioned.</li>
 * </ul>
 *
 * <p><b>Transaction boundary (TASK-BE-402 M1 fix).</b> The synchronous account-service
 * provisioning/lock/deactivate HTTP call (read-timeout up to 10s) MUST NOT run inside the
 * {@code sellers}-row DB transaction — otherwise a slow/hanging account-service would hold
 * the row lock + DB connection for the whole IAM timeout and risk connection-pool exhaustion.
 * So this service is NOT {@code @Transactional}; it orchestrates SHORT DB txns via
 * {@link SellerLifecyclePersistence} (a separate Spring bean so the {@code @Transactional}
 * proxy applies) and performs every IAM HTTP call BETWEEN/AFTER those short txns, outside any
 * DB transaction. Fail-soft semantics are unchanged (the provisioner swallows IAM failures).
 * The {@code TenantContext} ThreadLocal stays bound across the short txns + the HTTP call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterSellerService {

    private final SellerLifecyclePersistence persistence;
    private final SellerAccountProvisioner provisioner;

    /**
     * Onboards a seller (D2/D3/D5): persist {@code PENDING_PROVISIONING} in a SHORT tx, then
     * attempt fail-soft IAM provisioning OUTSIDE that tx. Returns the seller id regardless of
     * provisioning outcome — onboarding never blocks on IAM infra.
     */
    public String register(RegisterSellerCommand command) {
        Seller seller = Seller.register(command.sellerId(), command.displayName());
        // SHORT tx #1: persist PENDING. Re-onboard of an already-provisioned seller is
        // harmless: save() returns the existing aggregate; only attempt provisioning while
        // still PENDING (no-overwrite).
        Seller saved = persistence.save(seller);
        if (saved.isPendingProvisioning()) {
            attemptProvisioning(saved); // HTTP call OUTSIDE the DB tx + its own short update tx
        }
        return saved.getSellerId();
    }

    /**
     * Re-provision trigger (D3 retry) for a seller stuck in {@code PENDING_PROVISIONING}
     * (e.g. IAM was unavailable at onboarding), AND identity-reconciliation top-up (m2): an
     * ACTIVE seller with a null {@code identity_id} re-resolves the born-unified identity and
     * fills it. The account/identity HTTP call runs OUTSIDE the DB tx; the resulting status/id
     * update is its own short tx. Idempotent: an ACTIVE seller whose identity is already set is
     * a no-op.
     */
    public void provisionPending(String sellerId) {
        Seller seller = persistence.getOrThrow(sellerId);
        if (seller.isPendingProvisioning() || seller.needsIdentityReconciliation()) {
            attemptProvisioning(seller);
            return;
        }
        // already provisioned (account + identity set) or terminal — no-op
    }

    /**
     * Operator SUSPEND (D4): seller → SUSPENDED + lock the backing account.
     *
     * <p>Allowed from {@code PENDING_PROVISIONING} as well as {@code ACTIVE} (m1, intentional):
     * an operator may suspend a seller that never finished provisioning. With a null
     * {@code account_id} (legacy / still-PENDING) the lock is net-zero (no IAM call). The DB
     * state change is a SHORT tx; the IAM lock HTTP call runs OUTSIDE it.
     */
    public void suspend(String sellerId) {
        Seller seller = persistence.getOrThrow(sellerId);
        if (seller.suspend()) {
            persistence.update(seller); // SHORT tx
            // null-safe + idempotent: no backing account → net-zero no-op (D4). OUTSIDE the tx.
            provisioner.lockAccount(TenantContext.currentTenant(), seller.getAccountId());
        }
    }

    /**
     * Reverse lifecycle projection (ADR-MONO-042 D4-C, TASK-BE-421): an IAM
     * {@code account.status.changed → LOCKED} on the backing account suspends the matching
     * marketplace seller. This is the reverse of {@link #suspend(String)} and CRITICALLY does
     * NOT call {@code provisioner.lockAccount} — the account is ALREADY locked (IAM is the
     * source of this event), so re-locking would create a forward/back loop. The forward
     * {@link #suspend(String)} re-emits {@code LOCKED}, which loops back here and is an
     * already-SUSPENDED idempotent no-op.
     *
     * <p>Fail-soft / race-tolerant:
     * <ul>
     *   <li>no seller for this {@code account_id} → no-op (returns {@code false}); the consumer
     *       logs and skips (a locked account need not back a seller in this tenant).</li>
     *   <li>already SUSPENDED → {@code suspend()} returns {@code false}, no persist (idempotent
     *       re-delivery + forward-loop-back).</li>
     *   <li>CLOSED seller → {@code suspend()} throws {@link IllegalStateException}; the caller
     *       tolerates the race (a CLOSED seller is terminal and need not be re-suspended).</li>
     * </ul>
     *
     * @return {@code true} iff a seller was found and transitioned ACTIVE/PENDING → SUSPENDED.
     * @throws IllegalStateException if the matched seller is CLOSED (caller tolerates the race).
     */
    public boolean suspendByLockedAccount(String accountId) {
        Optional<Seller> match = persistence.findByAccountId(accountId);
        if (match.isEmpty()) {
            return false;
        }
        Seller seller = match.get();
        if (seller.suspend()) {
            persistence.update(seller); // SHORT tx — no IAM call (account already locked)
            return true;
        }
        return false;
    }

    /**
     * Operator CLOSE (D4): seller → CLOSED (terminal) + deactivate the backing account.
     *
     * <p>Allowed from {@code PENDING_PROVISIONING} as well as {@code ACTIVE}/{@code SUSPENDED}
     * (m1, intentional): an operator may close a seller that never finished provisioning. The DB
     * state change is a SHORT tx; the IAM deactivate HTTP call runs OUTSIDE it.
     */
    public void close(String sellerId) {
        Seller seller = persistence.getOrThrow(sellerId);
        if (seller.close()) {
            persistence.update(seller); // SHORT tx
            provisioner.deactivateAccount(TenantContext.currentTenant(), seller.getAccountId());
        }
    }

    /**
     * Idempotently ensures the per-tenant default seller exists (D8 degradation anchor,
     * AC-5) — born ACTIVE, never IAM-provisioned (it is the standalone single-store
     * anchor, not a real marketplace seller principal).
     */
    public String ensureDefaultSeller() {
        return persistence.ensureDefaultSeller().getSellerId();
    }

    /**
     * Attempts fail-soft IAM provisioning (D3) OUTSIDE any DB transaction and, on success,
     * transitions the seller (PENDING → ACTIVE and/or fills a null account/identity) in a SHORT
     * update tx. On failure the seller stays PENDING_PROVISIONING (the provisioner already
     * logged a warn) — retryable. An ACTIVE-seller identity top-up that yields no new id is a
     * no-op (no redundant update).
     */
    private void attemptProvisioning(Seller seller) {
        // HTTP call — OUTSIDE any DB transaction.
        ProvisioningResult result = provisioner.provision(
                TenantContext.currentTenant(), seller.getSellerId(), seller.getDisplayName());
        if (result.successful()) {
            boolean changed = seller.markProvisioned(result.accountId(), result.identityId());
            if (changed) {
                persistence.update(seller); // SHORT tx
                log.info("seller provisioned/reconciled tenant={} seller={} status={}",
                        TenantContext.currentTenant(), seller.getSellerId(), seller.getStatus());
            }
        } else {
            log.warn("seller left PENDING_PROVISIONING (IAM unavailable, retryable) "
                    + "tenant={} seller={}", TenantContext.currentTenant(), seller.getSellerId());
        }
    }
}
