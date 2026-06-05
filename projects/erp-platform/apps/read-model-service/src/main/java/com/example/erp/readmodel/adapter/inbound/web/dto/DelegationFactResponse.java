package com.example.erp.readmodel.adapter.inbound.web.dto;

import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response shape for a delegation fact (read-model-api.md § Delegation facts;
 * TASK-ERP-BE-015). The delegator/delegate are opaque employee ids (a console
 * drill-down resolves display names via the org-view if needed). {@code validFrom}
 * / {@code validTo} / {@code reason} / {@code revokedAt} are ABSENT (NON_NULL)
 * when not applicable — an out-of-order revoke-before-grant leaves the validity
 * window absent (no fabrication, E5).
 *
 * <p>{@code scope} ({@code GLOBAL}|{@code REQUEST}) + {@code scopeRequestId} are the
 * grant-time scoping (TASK-ERP-BE-018). NON_NULL → {@code scope} is ABSENT for a
 * revoke-only (out-of-order) row whose scope is unknown, and {@code scopeRequestId}
 * is ABSENT for a {@code GLOBAL} grant (only present when {@code scope == REQUEST}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DelegationFactResponse(
        String grantId,
        String status,
        String delegatorId,
        String delegateId,
        Instant validFrom,
        Instant validTo,
        String reason,
        Instant revokedAt,
        String scope,
        String scopeRequestId
) {

    public static DelegationFactResponse from(DelegationFactProjection fact) {
        return new DelegationFactResponse(
                fact.grantId(),
                fact.status() == null ? null : fact.status().name(),
                fact.delegatorId(),
                fact.delegateId(),
                fact.validFrom(),
                fact.validTo(),
                fact.reason(),
                fact.revokedAt(),
                fact.scope(),
                fact.scopeRequestId());
    }
}
