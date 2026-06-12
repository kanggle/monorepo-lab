package com.example.finance.ledger.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable application audit row (fintech F6, audit-heavy trait). Records
 * actor / occurred_at / after-state / reason for every journal entry posting,
 * written in the SAME transaction as the entry so a partial commit is impossible.
 *
 * <p><b>Append-only</b>: there are no mutators and the adapter only ever
 * {@code save}s new rows; the Flyway DDL grants no UPDATE/DELETE path. JPA
 * annotations are the allowed domain↔framework exception.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "aggregate_type", length = 40, nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 64, nullable = false)
    private String aggregateId;

    @Column(name = "action", length = 40, nullable = false)
    private String action;

    @Column(name = "actor", length = 64, nullable = false)
    private String actor;

    @Column(name = "after_state", length = 1024)
    private String afterState;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static AuditLog of(String tenantId, String aggregateType, String aggregateId,
                              String action, String actor, String afterState,
                              String reason, Instant occurredAt) {
        AuditLog row = new AuditLog();
        row.tenantId = tenantId;
        row.aggregateType = aggregateType;
        row.aggregateId = aggregateId;
        row.action = action;
        row.actor = actor;
        row.afterState = afterState;
        row.reason = reason;
        row.occurredAt = occurredAt;
        return row;
    }
}
