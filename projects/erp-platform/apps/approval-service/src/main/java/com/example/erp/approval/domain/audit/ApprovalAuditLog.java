package com.example.erp.approval.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable application audit row (erp E2 / E4 / E8 + A2 / A3 / A7). Records
 * actor / occurred_at / action / before_state / after_state / reason / outcome
 * for every approval transition, written in the SAME Tx as the state change +
 * outbox row (A7 atomicity). {@code event_id} is a unique idempotent key.
 *
 * <p><b>Append-only</b>: there are no mutators and the adapter only ever
 * {@code save}s new rows; the {@link ApprovalAuditLogRepository} port exposes
 * only {@code append}. JPA annotations are the allowed domain↔framework
 * exception.
 */
@Entity
@Table(name = "approval_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalAuditLog {

    public enum Outcome { SUCCESS, FAILURE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 48, nullable = false, unique = true)
    private String eventId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "aggregate_type", length = 40, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 48, nullable = false)
    private String aggregateId;

    @Column(name = "action", length = 40, nullable = false)
    private String action;

    @Column(name = "actor", length = 128, nullable = false)
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "json")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "json")
    private String afterState;

    @Column(name = "reason", length = 512)
    private String reason;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "outcome", length = 16, nullable = false)
    private Outcome outcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static ApprovalAuditLog of(String eventId, String tenantId, String aggregateId,
                                      String action, String actor, String beforeState,
                                      String afterState, String reason, Instant occurredAt) {
        ApprovalAuditLog row = new ApprovalAuditLog();
        row.eventId = eventId;
        row.tenantId = tenantId;
        row.aggregateType = "ApprovalRequest";
        row.aggregateId = aggregateId;
        row.action = action;
        row.actor = actor;
        row.beforeState = beforeState;
        row.afterState = afterState;
        row.reason = reason;
        row.outcome = Outcome.SUCCESS;
        row.occurredAt = occurredAt;
        return row;
    }
}
