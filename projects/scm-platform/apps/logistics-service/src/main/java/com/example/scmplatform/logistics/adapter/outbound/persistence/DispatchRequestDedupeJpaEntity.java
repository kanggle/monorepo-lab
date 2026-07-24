package com.example.scmplatform.logistics.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Vendor idempotency ground-truth row (I4). {@code request_id = shipment.id} (the stable
 * Idempotency-Key); {@code response_snapshot} is the cached vendor ack a repeat send returns
 * without a network call. Package-private persistence detail.
 */
@Entity
@Table(name = "dispatch_request_dedupe")
@Getter
@Setter
@NoArgsConstructor
class DispatchRequestDedupeJpaEntity {

    @Id
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "vendor", nullable = false)
    private String vendor;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "response_snapshot", nullable = false)
    private String responseSnapshot;
}
