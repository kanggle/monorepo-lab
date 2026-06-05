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
        Instant revokedAt
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
                fact.revokedAt());
    }
}
