package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA row for {@code inbound_expectations} — the 3PL inbound-expectation sink
 * (ADR-MONO-055 §D4 / TASK-SCM-BE-049).
 */
@Entity
@Table(name = "inbound_expectations")
@Getter
@Setter
@NoArgsConstructor
public class InboundExpectationJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "node_id", nullable = false, length = 36)
    private String nodeId;

    @Column(name = "sku", nullable = false, length = 100)
    private String sku;

    @Column(name = "expected_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(name = "source_po_id", nullable = false, length = 36)
    private String sourcePoId;

    @Column(name = "source_po_number", nullable = false, length = 40)
    private String sourcePoNumber;

    @Column(name = "expected_at")
    private LocalDate expectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExpectationStatusJpa status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "satisfied_at")
    private Instant satisfiedAt;

    public enum ExpectationStatusJpa {
        OPEN, SATISFIED
    }
}
