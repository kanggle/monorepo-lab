package com.wms.outbound.adapter.out.tms.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity backing {@code tms_request_dedupe} (V13 migration). Append-only
 * — never updated after the initial insert.
 *
 * <p>Stores the JSON snapshot as text on the JPA side; the column is
 * {@code JSONB} on Postgres (per V13 migration). {@link JdbcTypeCode}
 * with {@link SqlTypes#JSON} guards against accidental
 * {@code bytea}/{@code String} drift across Hibernate type detection.
 */
@Entity
@Table(name = "tms_request_dedupe")
public class TmsRequestDedupeEntity {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_snapshot", nullable = false, columnDefinition = "jsonb")
    private String responseSnapshot;

    protected TmsRequestDedupeEntity() {
    }

    public TmsRequestDedupeEntity(UUID requestId, Instant sentAt, String responseSnapshot) {
        this.requestId = requestId;
        this.sentAt = sentAt;
        this.responseSnapshot = responseSnapshot;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }
}
