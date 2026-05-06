package com.example.scmplatform.procurement.domain.po.status;

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

import java.time.Instant;

/**
 * Append-only PO state-transition history entry (S7). Inserted in the same
 * transaction as the {@code purchase_orders.status} update.
 */
@Entity
@Table(name = "po_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_id", length = 36, nullable = false)
    private String poId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30, nullable = false)
    private PoStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30, nullable = false)
    private PoStatus toStatus;

    @Column(name = "actor_account_id", length = 36)
    private String actorAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 20, nullable = false)
    private ActorType actorType;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static PoStatusHistory record(String poId,
                                         String tenantId,
                                         PoStatus from,
                                         PoStatus to,
                                         ActorType actorType,
                                         String actorAccountId,
                                         String reason) {
        PoStatusHistory h = new PoStatusHistory();
        h.poId = poId;
        h.tenantId = tenantId;
        h.fromStatus = from;
        h.toStatus = to;
        h.actorType = actorType;
        h.actorAccountId = actorAccountId;
        h.reason = reason;
        h.occurredAt = Instant.now();
        return h;
    }
}
