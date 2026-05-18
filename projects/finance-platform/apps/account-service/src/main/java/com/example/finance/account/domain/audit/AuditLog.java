package com.example.finance.account.domain.audit;

import com.example.finance.account.domain.account.ActorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Immutable application audit row (fintech F6, audit-heavy trait). Records
 * actor / occurred_at / before_state / after_state / reason for every
 * fund/regulatory-affecting operation (account status transition, balance
 * mutation, txn settle/reversal, KYC change, sanction-queue entry).
 *
 * <p><b>Append-only</b>: there are no mutators and the adapter only ever
 * {@code save}s new rows; the Flyway DDL grants no UPDATE/DELETE path. Written
 * in the SAME transaction as the business state change so a partial commit is
 * impossible. JPA annotations are the allowed domain↔framework exception.
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

    @Column(name = "aggregate_id", length = 36, nullable = false)
    private String aggregateId;

    @Column(name = "action", length = 40, nullable = false)
    private String action;

    @Column(name = "actor_account_id", length = 64)
    private String actorAccountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "actor_type", length = 20, nullable = false)
    private ActorType actorType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "json")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "json")
    private String afterState;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static AuditLog of(String tenantId,
                              String aggregateType,
                              String aggregateId,
                              String action,
                              String actorAccountId,
                              ActorType actorType,
                              String beforeState,
                              String afterState,
                              String reason,
                              Instant occurredAt) {
        AuditLog row = new AuditLog();
        row.tenantId = tenantId;
        row.aggregateType = aggregateType;
        row.aggregateId = aggregateId;
        row.action = action;
        row.actorAccountId = actorAccountId;
        row.actorType = actorType;
        row.beforeState = beforeState;
        row.afterState = afterState;
        row.reason = reason;
        row.occurredAt = occurredAt;
        return row;
    }
}
