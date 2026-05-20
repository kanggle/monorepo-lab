package com.example.erp.masterdata.domain.common;

import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataReferenceViolationException;

/**
 * Master aggregate state machine — stateless, pure (erp E1, rules/traits/
 * transactional.md T4). Every retire transition flows through
 * {@link #ensureRetireAllowed} so callers cannot bypass the matrix.
 *
 * <p>Transition matrix (architecture.md § Aggregate lifecycles):
 * <ul>
 *   <li>ACTIVE → RETIRED (operator retire, no live references)</li>
 *   <li>RETIRED → ∅ (terminal — no reactivation; new effective revision via
 *       PATCH would itself transition through the application path)</li>
 * </ul>
 *
 * <p>Self-transitions are forbidden so callers cannot silently no-op a retire
 * request. ACTIVE → ACTIVE is also forbidden (would mask state drift).
 */
public final class MasterStatusMachine {

    private MasterStatusMachine() {
    }

    /**
     * Guard the ACTIVE → RETIRED transition. Throws
     * {@link MasterdataReferenceViolationException} carrying the aggregate kind
     * if {@code current} is already RETIRED (terminal); the
     * reference-integrity check itself is performed by
     * {@code ReferenceChecker} prior to invoking this guard.
     */
    public static void ensureRetireAllowed(MasterStatus current, String aggregateKind) {
        if (current == MasterStatus.RETIRED) {
            throw new MasterdataReferenceViolationException(
                    aggregateKind + " is already RETIRED — terminal");
        }
    }
}
