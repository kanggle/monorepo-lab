package com.example.erp.approval.application;

import com.example.erp.approval.application.port.outbound.EmployeeLookup;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalActorNotLinkedException;
import com.example.erp.approval.domain.error.ApprovalErrors.PersonResolveUnavailableException;

import java.util.Optional;

/**
 * «호출자의 직원» (approval-api.md § v2.4, TASK-MONO-776) — the one place the caller's JWT
 * {@code sub} is turned into the employee id every person field holds. Both application
 * services go through here so the write rule (linked AND {@code ACTIVE}) and the read rule
 * (linked, any status) cannot drift apart between approval and delegation.
 *
 * <p>The {@code sub} itself is still the authenticated principal: it stays on
 * {@code audit_log.actor} and on the delegation grant's {@code created_by}/{@code revoked_by}
 * (who logged in), while the person fields hold who that login acts as.
 */
final class ActingEmployee {

    private ActingEmployee() {
    }

    /**
     * Writes (create / submit / approve / reject / withdraw / delegation create): the caller
     * must be linked to an {@code ACTIVE} employee — else 403 {@code APPROVAL_ACTOR_NOT_LINKED}.
     * «Could not ask» → 503 {@code SERVICE_UNAVAILABLE}, never «not linked».
     */
    static String require(MasterDataPort masterDataPort, ActorContext actor) {
        EmployeeLookup me = masterDataPort.callerEmployee(actor.actorId(), actor.tenantId());
        if (me.isUnavailable()) {
            throw new PersonResolveUnavailableException(
                    "could not resolve the calling account's employee (masterdata unavailable)");
        }
        if (!me.isFound()) {
            throw new ApprovalActorNotLinkedException(
                    "the calling account is not linked to an employee in this tenant");
        }
        if (!me.isActive()) {
            throw new ApprovalActorNotLinkedException(
                    "the employee linked to the calling account ('" + me.id() + "') is "
                            + me.status() + ", not ACTIVE");
        }
        return me.id();
    }

    /**
     * Reads (inbox / participant lists): the linked employee whatever its status, or empty when
     * the caller is unlinked (the contract answers that with an empty page, not an error).
     */
    static Optional<String> forRead(MasterDataPort masterDataPort, ActorContext actor) {
        EmployeeLookup me = masterDataPort.callerEmployee(actor.actorId(), actor.tenantId());
        if (me.isUnavailable()) {
            throw new PersonResolveUnavailableException(
                    "could not resolve the calling account's employee (masterdata unavailable)");
        }
        return me.isFound() ? Optional.of(me.id()) : Optional.empty();
    }
}
