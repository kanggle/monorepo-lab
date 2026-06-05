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
 * JPA entity for the {@code approval_fact_proj} latest-fact projection
 * (TASK-ERP-BE-010). One row per {@code approvalRequestId} (= aggregateId);
 * the consumer-side latest-state upsert is the only mutation (read-only API
 * otherwise, E5). {@code submitted_at} / {@code finalized_at} / {@code last_reason}
 * are nullable (ABSENT when not applicable — never fabricated).
 */
@Entity
@Table(name = "approval_fact_proj")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalFactProjJpaEntity {

    @Id
    @Column(name = "approval_request_id", nullable = false, length = 64)
    private String approvalRequestId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "subject_type", length = 16)
    private String subjectType;

    @Column(name = "subject_id", length = 64)
    private String subjectId;

    @Column(name = "approver_id", length = 64)
    private String approverId;

    @Column(name = "submitter_id", length = 64)
    private String submitterId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "last_reason", length = 512)
    private String lastReason;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "last_event_id", nullable = false, length = 64)
    private String lastEventId;
}
