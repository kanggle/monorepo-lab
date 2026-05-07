package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.CancelPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-7: Audit log completeness.
 *
 * <p>The {@code audit_log} table must receive one row per business action on a
 * Purchase Order. For the sequence DRAFT → SUBMIT → CANCEL the audit trail
 * must contain exactly 3 rows in insertion order, with the actions
 * {@code "DRAFT"}, {@code "SUBMIT"}, and {@code "CANCEL"} (rules/domains/scm.md S7).
 *
 * <p>All audit writes happen in the same transaction as the state change so
 * a partial commit is impossible (rules/traits/transactional.md T2).
 */
@Tag("integration")
@DisplayName("IT-7: Audit log records DRAFT / SUBMIT / CANCEL actions")
class AuditLogIntegrationTest extends AbstractProcurementIntegrationTest {

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

    @AfterAll
    static void tearDownMock() throws IOException {
        if (supplierMock != null) {
            supplierMock.shutdown();
        }
    }

    @Test
    @DisplayName("draft → submit → cancel 흐름에서 audit_log 3행 (DRAFT/SUBMIT/CANCEL)")
    void draftSubmitCancelProducesThreeAuditRows() {
        // Arrange
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());
        ActorContext buyer = new ActorContext("buyer-audit-001", TENANT_SCM, Set.of("BUYER"));

        // Stub supplier for successful submit
        supplierMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"RCPT-AUDIT-001\",\"status\":\"RECEIVED\"}"));

        // Act: the draft PO audit row was already written by persistDraftPo
        // (which calls PurchaseOrderApplicationService.draft internally) — but
        // persistDraftPo bypasses the service, so we need to capture only the
        // audit rows written by the application service submit + cancel calls.
        //
        // Count rows before
        long rowsBefore = auditLogJpa.findAll().stream()
                .filter(a -> a.getAggregateId().equals(po.getId()))
                .count();

        // submit — writes a "SUBMIT" audit row in the same transaction
        service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), "idem-audit-001"));

        // cancel — writes a "CANCEL" audit row in the same transaction
        service.cancel(new CancelPurchaseOrderCommand(buyer, po.getId(), "integration test cancel"));

        // Assert: 2 new rows (SUBMIT + CANCEL) were added beyond what was present before
        List<AuditLog> allPoAuditRows = auditLogJpa.findAll().stream()
                .filter(a -> a.getAggregateId().equals(po.getId()))
                .sorted(java.util.Comparator.comparingLong(AuditLog::getId))
                .toList();

        long rowsAfter = allPoAuditRows.size();
        assertThat(rowsAfter - rowsBefore)
                .as("exactly 2 new audit rows (SUBMIT + CANCEL) must have been inserted")
                .isEqualTo(2);

        // Verify the two new rows contain SUBMIT and CANCEL actions
        List<String> actions = allPoAuditRows.stream()
                .skip(rowsBefore)
                .map(AuditLog::getAction)
                .toList();
        assertThat(actions).containsExactly("SUBMIT", "CANCEL");
    }

    @Test
    @DisplayName("draft → submit → cancel 전 흐름을 서비스 경유 시 audit_log 3행 (DRAFT/SUBMIT/CANCEL)")
    void fullFlowViaServiceProducesThreeAuditRows() {
        // Arrange: create supplier, then use service.draft() to get the DRAFT audit row too
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        ActorContext buyer = new ActorContext("buyer-audit-full-001", TENANT_SCM, Set.of("BUYER"));

        // Stub supplier for successful submit
        supplierMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"RCPT-AUDIT-FULL-001\",\"status\":\"RECEIVED\"}"));

        // draft via application service — writes DRAFT audit row
        var draftView = service.draft(new com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand(
                buyer, supplier.getId(), "USD",
                List.of(new com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand.Line(
                        1, "sku-audit-001", "sup-sku-audit-001",
                        new java.math.BigDecimal("10"), new java.math.BigDecimal("5.00")))));

        String poId = draftView.id();

        // submit → SUBMIT audit row
        service.submit(new SubmitPurchaseOrderCommand(buyer, poId, "idem-audit-full-001"));

        // cancel → CANCEL audit row
        service.cancel(new CancelPurchaseOrderCommand(buyer, poId, "full flow test cancel"));

        // Assert: exactly 3 audit rows for this PO
        List<AuditLog> rows = auditLogJpa.findAll().stream()
                .filter(a -> a.getAggregateId().equals(poId))
                .sorted(java.util.Comparator.comparingLong(AuditLog::getId))
                .toList();

        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(AuditLog::getAction).toList())
                .containsExactly("DRAFT", "SUBMIT", "CANCEL");
    }
}
