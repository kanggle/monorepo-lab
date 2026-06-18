package com.example.product.application.service;

import com.example.product.application.command.RegisterSellerCommand;
import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import com.example.product.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *   <li>{@link #provisionPending} — the retry trigger for a PENDING seller (re-provision).</li>
 *   <li>{@link #suspend} / {@link #close} — operator deactivation; locks/deactivates the
 *       backing account (D4, idempotent + null-safe / net-zero).</li>
 *   <li>{@link #ensureDefaultSeller} — the per-tenant default seller (D8 anchor), born
 *       ACTIVE and never IAM-provisioned.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterSellerService {

    private final SellerRepository sellerRepository;
    private final SellerAccountProvisioner provisioner;

    /**
     * Onboards a seller (D2/D3/D5): persist {@code PENDING_PROVISIONING}, then attempt
     * fail-soft IAM provisioning. Returns the seller id regardless of provisioning
     * outcome — onboarding never blocks on IAM infra.
     */
    @Transactional
    public String register(RegisterSellerCommand command) {
        Seller seller = Seller.register(command.sellerId(), command.displayName());
        Seller saved = sellerRepository.save(seller);
        // Re-onboard of an already-provisioned seller is harmless: save() returns the
        // existing aggregate; only attempt provisioning while still PENDING (no-overwrite).
        if (saved.isPendingProvisioning()) {
            attemptProvisioning(saved);
        }
        return saved.getSellerId();
    }

    /**
     * Re-provision trigger (D3 retry) for a seller stuck in {@code PENDING_PROVISIONING}
     * (e.g. IAM was unavailable at onboarding). Idempotent: an already-ACTIVE seller is a
     * no-op; a successful provision fills the null account/identity and flips to ACTIVE.
     */
    @Transactional
    public void provisionPending(String sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
        if (!seller.isPendingProvisioning()) {
            return; // already provisioned (or terminal) — no-op
        }
        attemptProvisioning(seller);
    }

    /** Operator SUSPEND (D4): seller ACTIVE → SUSPENDED + lock the backing account. */
    @Transactional
    public void suspend(String sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
        if (seller.suspend()) {
            sellerRepository.update(seller);
            // null-safe + idempotent: no backing account → net-zero no-op (D4).
            provisioner.lockAccount(TenantContext.currentTenant(), seller.getAccountId());
        }
    }

    /** Operator CLOSE (D4): seller → CLOSED (terminal) + deactivate the backing account. */
    @Transactional
    public void close(String sellerId) {
        Seller seller = sellerRepository.findById(sellerId)
                .orElseThrow(() -> new SellerNotFoundException(sellerId));
        if (seller.close()) {
            sellerRepository.update(seller);
            provisioner.deactivateAccount(TenantContext.currentTenant(), seller.getAccountId());
        }
    }

    /**
     * Idempotently ensures the per-tenant default seller exists (D8 degradation anchor,
     * AC-5) — born ACTIVE, never IAM-provisioned (it is the standalone single-store
     * anchor, not a real marketplace seller principal).
     */
    @Transactional
    public String ensureDefaultSeller() {
        return sellerRepository.ensureDefaultSeller().getSellerId();
    }

    /**
     * Attempts fail-soft IAM provisioning (D3) and, on success, transitions the seller to
     * ACTIVE with the minted account/identity stored. On failure the seller stays
     * PENDING_PROVISIONING (the provisioner already logged a warn) — retryable.
     */
    private void attemptProvisioning(Seller seller) {
        ProvisioningResult result = provisioner.provision(
                TenantContext.currentTenant(), seller.getSellerId(), seller.getDisplayName());
        if (result.successful()) {
            seller.markProvisioned(result.accountId(), result.identityId());
            sellerRepository.update(seller);
            log.info("seller provisioned ACTIVE tenant={} seller={}",
                    TenantContext.currentTenant(), seller.getSellerId());
        } else {
            log.warn("seller left PENDING_PROVISIONING (IAM unavailable, retryable) "
                    + "tenant={} seller={}", TenantContext.currentTenant(), seller.getSellerId());
        }
    }
}
