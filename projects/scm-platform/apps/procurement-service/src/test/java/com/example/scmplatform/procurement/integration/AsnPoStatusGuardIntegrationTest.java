package com.example.scmplatform.procurement.integration;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.ReceiveAsnCommand;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-SCM-BE-063 — the ASN use case passes the PO through {@code PoStatusMachine}'s SYSTEM
 * rules against real PostgreSQL. A PO that is not {@code CONFIRMED} / {@code PARTIALLY_RECEIVED}
 * answers {@code PO_STATUS_TRANSITION_INVALID} and the transaction leaves <b>no trace</b>: PO
 * status, line received quantity, {@code po_status_history}, {@code advance_shipment_notices}
 * and {@code procurement_outbox} are all unchanged (Failure Scenario «422 while the ASN row is
 * already stored»).
 *
 * <p>Each test runs in its own tenant ({@code be063-<random>}) so absolute counts hold in a
 * database shared with the other ITs. POs are placed in the wanted status with one SQL UPDATE
 * (the same seeding convention as {@code PurchaseOrderMetricsIntegrationTest}).
 */
@Tag("integration")
@DisplayName("TASK-SCM-BE-063: ASN receipt is gated by the PO status machine (PostgreSQL)")
class AsnPoStatusGuardIntegrationTest extends AbstractProcurementIntegrationTest {

    @Autowired
    private PurchaseOrderApplicationService service;

    @Autowired
    private JdbcTemplate jdbc;

    private String seedPo(String tenant, PoStatus status) {
        String poId = UuidV7.randomString();
        PurchaseOrder po = PurchaseOrder.createDraft(poId, tenant,
                "PO-063-" + poId.replace("-", "").substring(20).toUpperCase(), "sup-063", "buyer-063", "KRW");
        PurchaseOrderLine line = PurchaseOrderLine.create(UuidV7.randomString(), poId, tenant,
                1, "sku-063", null, new BigDecimal("10"), new BigDecimal("100"));
        po.addLine(line);
        poJpa.save(po);
        lineJpa.save(line);
        jdbc.update("UPDATE purchase_orders SET status = ? WHERE id = ?", status.name(), poId);
        return poId;
    }

    private String lineId(String poId) {
        return lineJpa.findByPoIdOrderByLineNoAsc(poId).get(0).getId();
    }

    private ReceiveAsnCommand asn(String tenant, String poId, String ref, String qty) {
        return new ReceiveAsnCommand(tenant, poId, ref, Instant.now().plus(1, ChronoUnit.DAYS),
                List.of(new ReceiveAsnCommand.AsnLine(lineId(poId), new BigDecimal(qty))));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private void assertNoTrace(String tenant, String poId, String ref, PoStatus status) {
        assertThat(poJpa.findByIdAndTenantId(poId, tenant).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(lineJpa.findByPoIdOrderByLineNoAsc(poId).get(0).getReceivedQuantity())
                .isEqualByComparingTo("0");
        assertThat(count("SELECT COUNT(*) FROM po_status_history WHERE po_id = ?", poId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM advance_shipment_notices WHERE tenant_id = ? AND supplier_asn_ref = ?",
                tenant, ref)).isZero();
        assertThat(count("SELECT COUNT(*) FROM procurement_outbox WHERE aggregate_id = ?", poId)).isZero();
    }

    @ParameterizedTest(name = "🔴 full ASN on {0} → PO_STATUS_TRANSITION_INVALID, no trace")
    @EnumSource(value = PoStatus.class, names = {"CANCELED", "SUBMITTED", "ACKNOWLEDGED", "DRAFT"})
    void fullAsnOnIllegalStatusLeavesNoTrace(PoStatus status) {
        String tenant = "be063-" + UUID.randomUUID().toString().substring(0, 8);
        String poId = seedPo(tenant, status);
        String ref = "ASN-063-" + UUID.randomUUID();

        assertThatThrownBy(() -> service.receiveAsn(asn(tenant, poId, ref, "10")))
                .isInstanceOfSatisfying(PoStatusTransitionInvalidException.class, e -> {
                    assertThat(e.getFrom()).isEqualTo(status);
                    assertThat(e.getTo()).isEqualTo(PoStatus.RECEIVED);
                });

        assertNoTrace(tenant, poId, ref, status);
    }

    @Test
    @DisplayName("🔴 partial ASN on CANCELED → PO_STATUS_TRANSITION_INVALID (CANCELED→PARTIALLY_RECEIVED), no trace")
    void partialAsnOnCanceledLeavesNoTrace() {
        String tenant = "be063-" + UUID.randomUUID().toString().substring(0, 8);
        String poId = seedPo(tenant, PoStatus.CANCELED);
        String ref = "ASN-063-" + UUID.randomUUID();

        assertThatThrownBy(() -> service.receiveAsn(asn(tenant, poId, ref, "4")))
                .isInstanceOfSatisfying(PoStatusTransitionInvalidException.class,
                        e -> assertThat(e.getTo()).isEqualTo(PoStatus.PARTIALLY_RECEIVED));

        assertNoTrace(tenant, poId, ref, PoStatus.CANCELED);
    }

    @Test
    @DisplayName("control: CONFIRMED → partial → PARTIALLY_RECEIVED → remainder → RECEIVED; one history row per ASN; replay of an accepted ASN returns it unchanged")
    void controlGroupAndReplay() {
        String tenant = "be063-" + UUID.randomUUID().toString().substring(0, 8);
        String poId = seedPo(tenant, PoStatus.CONFIRMED);
        String first = "ASN-063-A-" + UUID.randomUUID();
        String second = "ASN-063-B-" + UUID.randomUUID();

        var a = service.receiveAsn(asn(tenant, poId, first, "4"));
        assertThat(poJpa.findByIdAndTenantId(poId, tenant).orElseThrow().getStatus())
                .isEqualTo(PoStatus.PARTIALLY_RECEIVED);
        service.receiveAsn(asn(tenant, poId, second, "6"));
        assertThat(poJpa.findByIdAndTenantId(poId, tenant).orElseThrow().getStatus())
                .isEqualTo(PoStatus.RECEIVED);

        assertThat(jdbc.queryForList(
                "SELECT from_status || '>' || to_status FROM po_status_history WHERE po_id = ? ORDER BY id",
                String.class, poId))
                .containsExactly("CONFIRMED>PARTIALLY_RECEIVED", "PARTIALLY_RECEIVED>RECEIVED");

        // Replay of the first (accepted) ASN on a now-RECEIVED PO: the stored ASN, no new write.
        var replay = service.receiveAsn(asn(tenant, poId, first, "4"));
        assertThat(replay.id()).isEqualTo(a.id());
        assertThat(count("SELECT COUNT(*) FROM advance_shipment_notices WHERE po_id = ?", poId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM po_status_history WHERE po_id = ?", poId)).isEqualTo(2);
        assertThat(lineJpa.findByPoIdOrderByLineNoAsc(poId).get(0).getReceivedQuantity())
                .isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("replay of a REJECTED ASN is evaluated again (never stored) — rejected again while CANCELED")
    void replayOfRejectedAsn() {
        String tenant = "be063-" + UUID.randomUUID().toString().substring(0, 8);
        String poId = seedPo(tenant, PoStatus.CANCELED);
        String ref = "ASN-063-R-" + UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.receiveAsn(asn(tenant, poId, ref, "10")))
                    .isInstanceOf(PoStatusTransitionInvalidException.class);
        }
        assertNoTrace(tenant, poId, ref, PoStatus.CANCELED);
    }
}
