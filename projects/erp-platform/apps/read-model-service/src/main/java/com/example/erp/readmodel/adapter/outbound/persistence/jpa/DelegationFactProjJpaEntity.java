package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity for the {@code delegation_fact_proj} latest-fact projection
 * (TASK-ERP-BE-015). One row per {@code grantId} (= aggregateId); the
 * consumer-side latest-state upsert is the only mutation (read-only API
 * otherwise, E5). {@code valid_from} / {@code valid_to} / {@code reason} /
 * {@code revoked_at} are nullable (ABSENT when not applicable — never
 * fabricated; an out-of-order revoke-before-grant leaves the validity window
 * NULL).
 */
@Entity
@Table(name = "delegation_fact_proj")
@Getter
@Setter
@NoArgsConstructor
public class DelegationFactProjJpaEntity {

    @Id
    @Column(name = "grant_id", nullable = false, length = 64)
    private String grantId;

    /**
     * The tenant the grant belongs to, taken from the event envelope
     * (TASK-ERP-BE-043 / ADR-ERP-001 — D). The column has carried a legacy
     * {@code DEFAULT 'erp'} since V3 and was previously never mapped, so any row
     * this consumer wrote would have silently disagreed with its source of record
     * in {@code approval-service} (whose {@code delegation_grant.tenant_id} is the
     * customer tenant) — and would have tripped the single-tenant ratchet by
     * introducing a second distinct value into erp on its own.
     */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "delegator_id", length = 64)
    private String delegatorId;

    @Column(name = "delegate_id", length = 64)
    private String delegateId;

    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "last_event_id", nullable = false, length = 64)
    private String lastEventId;

    @Column(name = "scope", length = 16)
    private String scope;

    @Column(name = "scope_request_id", length = 64)
    private String scopeRequestId;
}
