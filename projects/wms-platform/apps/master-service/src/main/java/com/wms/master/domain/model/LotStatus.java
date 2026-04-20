package com.wms.master.domain.model;

/**
 * Lot lifecycle status. Extends the common ACTIVE/INACTIVE pair with an
 * additional terminal-for-reactivation {@link #EXPIRED} state set by the
 * scheduled domain job when {@code expiry_date < today}.
 *
 * <p>See {@code specs/services/master-service/domain-model.md} §6 for the
 * authoritative state machine — in particular:
 * <ul>
 *   <li>EXPIRED is terminal for {@code reactivate}; the scheduler transition
 *       is one-way from ACTIVE.
 *   <li>EXPIRED → INACTIVE is permitted (hide from listings).
 * </ul>
 */
public enum LotStatus {
    ACTIVE,
    INACTIVE,
    EXPIRED
}
