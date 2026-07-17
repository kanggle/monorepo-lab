package com.kanggle.platformconsole.bff.domain.composition;

import java.util.List;

/**
 * Domain rule: classifies a set of per-leg outcomes into the composition-level
 * degrade state.
 *
 * <p>Framework-free (no Spring imports). Application use-case stubs depend on
 * this for testing partial-failure rendering shape (architecture.md § Test Pyramid
 * application unit layer).
 *
 * <p>ADR-MONO-017 D5.A: aggregation degrade — responsive domains' cards +
 * per-failed-domain degraded card. All-down still returns the all-degraded
 * envelope (never blanks the dashboard).
 */
public final class DegradePolicy {

    private DegradePolicy() {}

    /**
     * Returns true if every leg is degraded or forbidden (all-down scenario).
     */
    public static boolean isAllDown(List<LegOutcome> outcomes) {
        return !outcomes.isEmpty() && outcomes.stream().noneMatch(LegOutcome::isOk);
    }
}
