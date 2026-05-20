package com.kanggle.platformconsole.bff.application.usecase;

import com.kanggle.platformconsole.bff.domain.composition.DegradePolicy;
import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Composition use-case placeholder for the "Operator Overview" cross-domain dashboard.
 *
 * <p>This is a skeleton stub at TASK-PC-BE-001 scope. The first concrete
 * composition route — fan-out across GAP + wms + scm + finance + erp with
 * per-domain card assembly — is implemented by TASK-PC-FE-011 (§ 2.4.9.1).
 *
 * <p>Application layer: depends only on outbound ports and domain rules.
 * Never reaches into HTTP infrastructure directly (architecture.md § Composition Routes).
 *
 * <p>Degrade policy (ADR-MONO-017 D5.A — all-down still returns 200 with all-degraded
 * envelope, never blanks the dashboard):
 * <ul>
 *   <li>Each leg's outcome is {@link LegOutcome#ok}, {@link LegOutcome#degraded},
 *       or {@link LegOutcome#forbidden}.</li>
 *   <li>Responsive domains supply their card data; failed domains supply a degraded card.</li>
 *   <li>{@link DegradePolicy} classifies the overall envelope shape.</li>
 * </ul>
 */
@Service
public class OperatorOverviewCompositionUseCase {

    private final CredentialSelectionPort credentialSelection;

    public OperatorOverviewCompositionUseCase(CredentialSelectionPort credentialSelection) {
        this.credentialSelection = credentialSelection;
    }

    /**
     * Skeleton stub — returns empty outcome list.
     *
     * <p>TASK-PC-FE-011 replaces this with the actual 5-domain fan-out.
     * The test harness for the degrade policy is in
     * {@code application.usecase.OperatorOverviewCompositionUseCaseTest}.
     */
    public List<LegOutcome> compose(String tenantId) {
        // Skeleton: no outbound ports wired yet (TASK-PC-FE-011 adds them).
        // The degrade policy and credential selection are exercised by tests
        // via the domain unit and application unit layers.
        return List.of();
    }
}
