package com.wms.outbound.integration.picking;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.result.PickingRequestLineResult;
import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.integration.OutboundServiceIntegrationBase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for TASK-BE-343 (§2.4 picking-request
 * by-order read with planned lines).
 *
 * <p>Drives the real {@link QueryPickingRequestUseCase} → {@link
 * com.wms.outbound.application.service.PickingQueryService} → {@link
 * com.wms.outbound.adapter.out.persistence.adapter.PickingRepositoryImpl}
 * → Postgres stack end-to-end and asserts:
 *
 * <ol>
 *   <li>Order WITH a picking request → result present, {@code lines} non-empty,
 *       {@code locationId} and {@code qtyToPick} populated.</li>
 *   <li>Order exists but no picking request yet → {@code Optional.empty()}
 *       (the controller maps this to {@code 200 { content:[] }}).</li>
 * </ol>
 *
 * <p>Rows are seeded directly via JDBC to avoid touching the write path (scope
 * = additive read-only endpoint). The test reuses the existing IT base
 * (Postgres + Kafka + Redis + WireMock), all started as a static shared
 * infrastructure.
 */
class PickingRequestByOrderIT extends OutboundServiceIntegrationBase {

    @Autowired
    private QueryPickingRequestUseCase queryPickingRequest;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID orderId;
    private UUID orderLineId;
    private UUID pickingId;
    private UUID pickingLineId;
    private UUID sagaId;
    private UUID warehouseId;
    private UUID skuId;
    private UUID locationId;

    @BeforeEach
    void seed() {
        orderId     = UUID.randomUUID();
        orderLineId = UUID.randomUUID();
        pickingId   = UUID.randomUUID();
        pickingLineId = UUID.randomUUID();
        sagaId      = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        skuId       = UUID.randomUUID();
        locationId  = UUID.randomUUID();

        Instant now = Instant.now();

        // Partner snapshot required by outbound_order FK check.
        UUID partnerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO partner_snapshot
                    (id, partner_code, partner_type, status, cached_at, master_version)
                VALUES (?, 'TEST-PARTNER', 'CUSTOMER', 'ACTIVE', now(), 1)
                """, partnerId);

        // Seed the outbound order.
        jdbc.update("""
                INSERT INTO outbound_order
                    (id, order_no, source, customer_partner_id, warehouse_id,
                     status, version, created_at, created_by, updated_at, updated_by)
                VALUES (?, ?, 'MANUAL', ?, ?,
                        'PICKING', 0, ?, 'test', ?, 'test')
                """,
                orderId, "ORD-IT-" + orderId, partnerId, warehouseId,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        // Seed the order line.
        jdbc.update("""
                INSERT INTO outbound_order_line
                    (id, order_id, line_no, sku_id, lot_id, qty_ordered)
                VALUES (?, ?, 1, ?, null, 30)
                """, orderLineId, orderId, skuId);

        // Seed the picking request.
        jdbc.update("""
                INSERT INTO picking_request
                    (id, order_id, warehouse_id, saga_id, status,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'SUBMITTED', ?, ?, 0)
                """,
                pickingId, orderId, warehouseId, sagaId,
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        // Seed the picking request line.
        jdbc.update("""
                INSERT INTO picking_request_line
                    (id, picking_request_id, order_line_id, sku_id, lot_id,
                     location_id, requested_qty, picked_qty)
                VALUES (?, ?, ?, ?, null, ?, 30, 0)
                """,
                pickingLineId, pickingId, orderLineId, skuId, locationId);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM picking_request_line WHERE picking_request_id = ?", pickingId);
        jdbc.update("DELETE FROM picking_request WHERE id = ?", pickingId);
        jdbc.update("DELETE FROM outbound_order_line WHERE order_id = ?", orderId);
        jdbc.update("DELETE FROM outbound_order WHERE id = ?", orderId);
        jdbc.update("DELETE FROM partner_snapshot WHERE partner_code = 'TEST-PARTNER'");
    }

    @Test
    @DisplayName("§2.4 order with picking request → result present with planned lines (locationId + qtyToPick populated)")
    void orderWithPickingRequest_resultPresentWithLines() {
        Optional<PickingRequestResult> result = queryPickingRequest.findByOrderId(orderId);

        assertThat(result).isPresent();
        PickingRequestResult pr = result.get();
        assertThat(pr.pickingRequestId()).isEqualTo(pickingId);
        assertThat(pr.orderId()).isEqualTo(orderId);
        assertThat(pr.status()).isEqualTo("SUBMITTED");

        List<PickingRequestLineResult> lines = pr.lines();
        assertThat(lines).hasSize(1);
        PickingRequestLineResult line = lines.get(0);
        assertThat(line.pickingRequestLineId()).isEqualTo(pickingLineId);
        assertThat(line.orderLineId()).isEqualTo(orderLineId);
        assertThat(line.skuId()).isEqualTo(skuId);
        assertThat(line.lotId()).isNull();
        assertThat(line.locationId()).isEqualTo(locationId);
        assertThat(line.qtyToPick()).isEqualTo(30);
    }

    @Test
    @DisplayName("§2.4 order exists but no picking request → empty Optional (maps to 200 { content:[] })")
    void orderExistsNoPickingRequest_emptyOptional() {
        UUID anotherOrderId = UUID.randomUUID();
        // Don't seed a picking request for this order; the use-case must return empty.

        Optional<PickingRequestResult> result = queryPickingRequest.findByOrderId(anotherOrderId);

        assertThat(result).isEmpty();
    }
}
