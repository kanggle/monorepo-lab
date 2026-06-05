package com.example.erp.approval.application.view;

import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Delegation grant response payload (approval-api.md § v2.1/v2.3 amendment).
 * Nullable fields ({@code validTo}, {@code reason}, {@code revokedAt},
 * {@code revokedBy}, {@code scopeRequestId}) are ABSENT per
 * {@code @JsonInclude(NON_NULL)} — never serialized as null. TASK-ERP-BE-017 —
 * {@code scope} is always present; {@code scopeRequestId} is present for a
 * REQUEST grant and ABSENT for GLOBAL.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DelegationGrantView(String id, String delegatorId, String delegateId,
                                  Instant validFrom, Instant validTo, String reason,
                                  String scope, String scopeRequestId,
                                  String status, Instant createdAt, String createdBy,
                                  Instant revokedAt, String revokedBy) {

    public static DelegationGrantView from(DelegationGrant g) {
        return new DelegationGrantView(
                g.getId(),
                g.getDelegatorId(),
                g.getDelegateId(),
                g.getValidFrom(),
                g.getValidTo(),
                g.getReason(),
                g.getScope().name(),
                g.getScopeRequestId(),
                g.getStatus().name(),
                g.getCreatedAt(),
                g.getCreatedBy(),
                g.getRevokedAt(),
                g.getRevokedBy());
    }
}
