package com.example.scmplatform.procurement.integration;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.ReceiveAsnCommand;
import com.example.scmplatform.procurement.domain.error.AsnOverreceiptException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistory;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.PoStatusHistoryJpaRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-6: ASN overreceipt.
 *
 * <p>When a supplier's ASN line quantity (15) exceeds the PO line's ordered
 * quantity (10), the application must reject the ASN with
 * {@link AsnOverreceiptException} (HTTP 422 {@code ASN_OVERRECEIPT}).
 *
 * <p>The PO must be in CONFIRMED status for {@code receiveAsn} to make a
 * status transition. The test sets up the full PO lifecycle:
 * DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED → then attempts an over-receipt.
 */
@Tag("integration")
@DisplayName("IT-6: ASN overreceipt → AsnOverreceiptException → 422")
class AsnOverreceiptIntegrationTest extends AbstractProcurementIntegrationTest {

    private static MockWebServer supplierMock;

    @DynamicPropertySource
    static void supplierMockUrl(DynamicPropertyRegistry registry) throws IOException {
        supplierMock = new MockWebServer();
        supplierMock.start();
        registry.add("scmplatform.procurement.supplier.mock.base-url",
                () -> "http://" + supplierMock.getHostName() + ":" + supplierMock.getPort());
    }

    @Autowired
    private PurchaseOrderApplicationService service;

    @Autowired
    private PoStatusHistoryJpaRepository historyJpa;

    @AfterEach
    void tearDown() throws IOException {
        if (supplierMock != null) {
            supplierMock.shutdown();
        }
    }

    @Test
    @DisplayName("PO line qty=10, ASN line qty=15 → AsnOverreceiptException")
    void asnLineQuantityExceedsPOLineQuantity() {
        // Arrange: persist supplier and DRAFT PO (single line qty=10).
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());

        // Advance PO to CONFIRMED state so receiveAsn is valid.
        advanceToConfirmed(po, supplier);

        // Reload to get persisted line id.
        PurchaseOrder confirmedPo = poJpa.findByIdAndTenantId(po.getId(), TENANT_SCM).orElseThrow();
        String poLineId = lineJpa.findByPoIdOrderByLineNoAsc(confirmedPo.getId())
                .get(0).getId();

        // Act: attempt to receive qty=15 against ordered qty=10.
        assertThatThrownBy(() ->
                service.receiveAsn(new ReceiveAsnCommand(
                        TENANT_SCM,
                        confirmedPo.getId(),
                        "ASN-OVER-001",
                        Instant.now().plusSeconds(3600),
                        List.of(new ReceiveAsnCommand.AsnLine(poLineId, new BigDecimal("15"))))))
                .isInstanceOf(AsnOverreceiptException.class)
                .hasMessageContaining("ASN_OVERRECEIPT");

        // Assert: PO status unchanged (still CONFIRMED after rollback).
        PurchaseOrder afterRejection = poJpa.findByIdAndTenantId(po.getId(), TENANT_SCM).orElseThrow();
        assertThat(afterRejection.getStatus()).isEqualTo(PoStatus.CONFIRMED);
    }

    /**
     * Helper — advance the given PO from DRAFT → SUBMITTED → ACKNOWLEDGED → CONFIRMED
     * using direct JPA/domain updates (bypasses supplier adapter call overhead).
     */
    private void advanceToConfirmed(PurchaseOrder po, Supplier supplier) {
        // SUBMITTED
        supplierMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"RCPT-OVR-001\",\"status\":\"RECEIVED\"}"));
        service.submit(new com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand(
                new com.example.scmplatform.procurement.application.ActorContext(
                        "buyer-ovr-001", TENANT_SCM, Set.of("BUYER")),
                po.getId(), "idem-ovr-001"));

        // ACKNOWLEDGED (webhook path)
        service.acknowledge(new com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand(
                TENANT_SCM, po.getId(), "ACK-OVR-001"));

        // CONFIRMED
        service.confirm(new com.example.scmplatform.procurement.application.command.ConfirmPurchaseOrderCommand(
                new com.example.scmplatform.procurement.application.ActorContext(
                        "operator-ovr-001", TENANT_SCM, Set.of("OPERATOR")),
                po.getId()));
    }
}
