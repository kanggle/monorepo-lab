package com.example.scmplatform.procurement.domain.po.status;

import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;

import java.util.Map;
import java.util.Set;

/**
 * PO state machine. Stateless utility — every transition flows through
 * {@link #ensureTransitionAllowed} so business logic cannot bypass the rules
 * (rules/traits/transactional.md T4).
 *
 * <p>Transition matrix (TASK-SCM-BE-002 § Acceptance Criteria #9;
 * CONFIRMED&rarr;CANCELED added by TASK-SCM-BE-036 / ADR-MONO-050 D6.3):
 * <ul>
 *   <li>BUYER:    DRAFT &rarr; SUBMITTED, DRAFT/SUBMITTED/ACKNOWLEDGED/CONFIRMED &rarr; CANCELED</li>
 *   <li>SUPPLIER: SUBMITTED &rarr; ACKNOWLEDGED</li>
 *   <li>OPERATOR: ACKNOWLEDGED &rarr; CONFIRMED, DRAFT/SUBMITTED/ACKNOWLEDGED/CONFIRMED &rarr; CANCELED</li>
 *   <li>SYSTEM:   CONFIRMED &rarr; PARTIALLY_RECEIVED, PARTIALLY_RECEIVED &rarr; RECEIVED,
 *                 CONFIRMED &rarr; RECEIVED (full ASN in one shot),
 *                 RECEIVED &rarr; SETTLED, SETTLED &rarr; CLOSED</li>
 * </ul>
 *
 * <p><strong>CONFIRMED&rarr;CANCELED is deliberately the ONLY cancel path added
 * beyond the pre-CONFIRMED states.</strong> A CONFIRMED PO is not-yet-received by
 * definition; once any goods arrive the PO is {@code PARTIALLY_RECEIVED} /
 * {@code RECEIVED}, and those keys are absent from every actor map, so the matrix
 * itself forbids cancelling a (partially-)received PO — that is
 * return/reverse-logistics, out of v1 scope (ADR-MONO-050 D6.3). Do not add a
 * {@code PARTIALLY_RECEIVED} / {@code RECEIVED} &rarr; CANCELED entry without a
 * returns domain to back it.
 *
 * <p>{@code CANCELED} and {@code CLOSED} are terminal — every outbound
 * transition is forbidden. Self-transitions are forbidden so callers cannot
 * silently no-op.
 */
public final class PoStatusMachine {

    private static final Map<ActorType, Map<PoStatus, Set<PoStatus>>> TRANSITIONS = Map.of(
            ActorType.BUYER, Map.of(
                    PoStatus.DRAFT, Set.of(PoStatus.SUBMITTED, PoStatus.CANCELED),
                    PoStatus.SUBMITTED, Set.of(PoStatus.CANCELED),
                    PoStatus.ACKNOWLEDGED, Set.of(PoStatus.CANCELED),
                    // ADR-MONO-050 D6.3 / SCM-BE-036: cancel a confirmed-but-not-yet-received PO.
                    PoStatus.CONFIRMED, Set.of(PoStatus.CANCELED)
            ),
            ActorType.OPERATOR, Map.of(
                    PoStatus.DRAFT, Set.of(PoStatus.SUBMITTED, PoStatus.CANCELED),
                    PoStatus.SUBMITTED, Set.of(PoStatus.CANCELED),
                    PoStatus.ACKNOWLEDGED, Set.of(PoStatus.CONFIRMED, PoStatus.CANCELED),
                    // ADR-MONO-050 D6.3 / SCM-BE-036: cancel a confirmed-but-not-yet-received PO.
                    PoStatus.CONFIRMED, Set.of(PoStatus.CANCELED)
            ),
            ActorType.SUPPLIER, Map.of(
                    PoStatus.SUBMITTED, Set.of(PoStatus.ACKNOWLEDGED)
            ),
            ActorType.SYSTEM, Map.of(
                    PoStatus.CONFIRMED, Set.of(PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED),
                    PoStatus.PARTIALLY_RECEIVED, Set.of(PoStatus.RECEIVED),
                    PoStatus.RECEIVED, Set.of(PoStatus.SETTLED),
                    PoStatus.SETTLED, Set.of(PoStatus.CLOSED)
            )
    );

    private static final Set<PoStatus> TERMINAL = Set.of(PoStatus.CANCELED, PoStatus.CLOSED);

    private PoStatusMachine() {
    }

    public static void ensureTransitionAllowed(PoStatus current, PoStatus target, ActorType actor) {
        if (TERMINAL.contains(current)) {
            throw new PoStatusTransitionInvalidException(current, target, actor);
        }
        if (current == target) {
            throw new PoStatusTransitionInvalidException(current, target, actor);
        }
        Set<PoStatus> allowed = TRANSITIONS
                .getOrDefault(actor, Map.of())
                .getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new PoStatusTransitionInvalidException(current, target, actor);
        }
    }

    public static boolean isTransitionAllowed(PoStatus current, PoStatus target, ActorType actor) {
        try {
            ensureTransitionAllowed(current, target, actor);
            return true;
        } catch (PoStatusTransitionInvalidException e) {
            return false;
        }
    }
}
