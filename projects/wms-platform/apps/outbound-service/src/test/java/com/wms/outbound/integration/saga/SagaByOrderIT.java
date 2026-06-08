package com.wms.outbound.integration.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.in.QuerySagaUseCase;
import com.wms.outbound.application.result.SagaResult;
import com.wms.outbound.integration.OutboundServiceIntegrationBase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for {@code GET /orders/{id}/saga}
 * (outbound-service-api.md §5.1).
 *
 * <p>Drives the real {@link QuerySagaUseCase} → {@link
 * com.wms.outbound.application.service.SagaQueryService} → the JPA-backed
 * {@link com.wms.outbound.application.port.out.SagaPersistencePort} adapter
 * against a live Postgres instance and asserts:
 *
 * <ol>
 *   <li>Order with a saga (status {@code RESERVED}) → result present,
 *       {@code state} = "RESERVED".</li>
 *   <li>Unknown order id → {@code Optional.empty()}.</li>
 * </ol>
 *
 * <p>Rows are seeded directly via JDBC to avoid touching the write path.
 * Each test uses a fresh {@link UUID#randomUUID()} for orderId so concurrent
 * runs do not clash. Cleanup removes only the rows introduced by this test.
 */
class SagaByOrderIT extends OutboundServiceIntegrationBase {

    @Autowired
    private QuerySagaUseCase querySaga;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID orderId;
    private UUID sagaId;

    @BeforeEach
    void seed() {
        orderId = UUID.randomUUID();
        sagaId  = UUID.randomUUID();

        Instant now = Instant.now();

        // Seed the outbound order (mirrors the proven column set used by
        // PickingRequestByOrderIT and TmsClientAdapterIT).
        jdbc.update("""
                INSERT INTO outbound_order
                    (id, erp_order_number, order_no, source, warehouse_id,
                     partner_id, customer_partner_id, status,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, 'MANUAL', ?, ?, ?, 'PICKING', ?, ?, 0)
                """,
                orderId,
                "ERP-" + orderId,
                "ORD-" + orderId,
                UUID.randomUUID(),   // warehouseId
                UUID.randomUUID(),   // partnerId
                UUID.randomUUID(),   // customerPartnerId
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));

        // Seed the outbound_saga (re_emit_count has a default of 0;
        // shipment_id + failure_reason are nullable — omitted here).
        jdbc.update("""
                INSERT INTO outbound_saga
                    (id, order_id, status, picking_request_id,
                     created_at, updated_at, version)
                VALUES (?, ?, 'RESERVED', ?, ?, ?, 0)
                """,
                sagaId,
                orderId,
                sagaId,   // picking_request_id == sagaId (v1 convention)
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM outbound_saga WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM outbound_order WHERE id = ?", orderId);
    }

    @Test
    @DisplayName("§5.1 order with RESERVED saga → result present, state=RESERVED")
    void orderWithReservedSaga_resultPresentWithState() {
        Optional<SagaResult> result = querySaga.findByOrderId(orderId);

        assertThat(result).isPresent();
        SagaResult r = result.get();
        assertThat(r.sagaId()).isEqualTo(sagaId);
        assertThat(r.orderId()).isEqualTo(orderId);
        assertThat(r.state()).isEqualTo("RESERVED");
        assertThat(r.failureReason()).isNull();
        assertThat(r.startedAt()).isNotNull();
        assertThat(r.lastTransitionAt()).isNotNull();
        assertThat(r.version()).isEqualTo(0L);
    }

    @Test
    @DisplayName("§5.1 unknown order id → empty Optional")
    void unknownOrderId_emptyOptional() {
        Optional<SagaResult> result = querySaga.findByOrderId(UUID.randomUUID());

        assertThat(result).isEmpty();
    }
}
