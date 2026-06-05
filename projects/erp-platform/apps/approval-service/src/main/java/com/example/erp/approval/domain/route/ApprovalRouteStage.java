package com.example.erp.approval.domain.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One persisted stage of a multi-stage approval route (TASK-ERP-BE-012,
 * architecture.md § v2.0 amendment). Rows for a request are ordered by
 * {@code stageIndex} (0-based) and form the {@link ApprovalRoute}. The legacy
 * v1.0 single-stage request is one row at {@code stageIndex = 0} (the Flyway V2
 * backfill).
 *
 * <p>JPA annotations are the allowed domain↔framework exception. Append-only at
 * create time — stages are fixed for the lifetime of a request (delegation is
 * v2.1; there is no in-place mutation).
 */
@Entity
@Table(name = "approval_route_stage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRouteStage {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "request_id", length = 48, nullable = false)
    private String requestId;

    @Column(name = "stage_index", nullable = false)
    private int stageIndex;

    @Column(name = "approver_id", length = 64, nullable = false)
    private String approverId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ApprovalRouteStage of(String id, String tenantId, String requestId,
                                        int stageIndex, String approverId, Instant createdAt) {
        ApprovalRouteStage s = new ApprovalRouteStage();
        s.id = id;
        s.tenantId = tenantId;
        s.requestId = requestId;
        s.stageIndex = stageIndex;
        s.approverId = approverId;
        s.createdAt = createdAt;
        return s;
    }

    public Approver toApprover() {
        return new Approver(approverId);
    }
}
