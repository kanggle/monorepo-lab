package com.example.finance.account.application.port.outbound;

import com.example.finance.account.domain.compliance.ScreeningDecision;
import com.example.finance.account.domain.money.Money;

/**
 * Outbound port for AML/sanction screening (architecture.md § KYC/AML
 * Compliance Gate, fintech F4). The single fund-movement application path
 * invokes {@link #screen} BEFORE any balance mutation — there is no bypass.
 *
 * <p>v1 = {@code StubComplianceAdapter} (deterministic, configurable sanction
 * list for tests). v2 swaps the adapter behind this same port (no domain
 * change). External vendor SDKs MUST stay behind this port (fintech
 * Integration Boundaries) — never imported into domain/application.
 *
 * <p>Forbidden-pattern note: an indeterminate external response must NOT be
 * optimistically confirmed; v1 has no real external adapter, the port contract
 * documents the "indeterminate → reconcile later" rule for v2.
 */
public interface CompliancePort {

    /**
     * Screen a fund movement.
     *
     * @param ownerRef             account owner reference (regulated — adapters
     *                             MUST NOT log it in plaintext, F7)
     * @param counterpartyOwnerRef counterparty owner ref (transfer) or null
     * @param amount               the fund-movement amount
     * @return a {@link ScreeningDecision} (CLEAR / SANCTION_HIT / UNRESOLVED)
     */
    ScreeningDecision screen(String ownerRef, String counterpartyOwnerRef, Money amount);
}
