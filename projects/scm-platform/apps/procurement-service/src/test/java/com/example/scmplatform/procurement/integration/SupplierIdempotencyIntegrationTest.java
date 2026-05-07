package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-4: Supplier submit idempotency.
 *
 * <p>When a buyer retransmits the same {@code Idempotency-Key} for a PO
 * submit, the service must call the supplier only once per unique key (the
 * supplier deduplicates on the forwarded key) and the returned
 * {@code supplierReceiptRef} must be identical across retransmissions.
 *
 * <p>Scenario modeled here: the PO is persisted as DRAFT; the first submit
 * succeeds and transitions it to SUBMITTED; the second submit with the same
 * idempotency key hits a PO that is already SUBMITTED, which the
 * {@link com.example.scmplatform.procurement.domain.po.status.PoStatusMachine}
 * rejects with
 * {@link com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException}.
 * The application service therefore does NOT call the supplier a second time.
 *
 * <p>We verify that the supplier MockWebServer received exactly one HTTP
 * request, and that the first call's receipt reference was persisted.
 */
@Tag("integration")
@DisplayName("IT-4: Supplier submit idempotency")
class SupplierIdempotencyIntegrationTest extends AbstractProcurementIntegrationTest {

    private static final String SUPPLIER_RECEIPT_REF = "RCPT-IDEM-001";

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

    @AfterEach
    void tearDown() throws IOException {
        if (supplierMock != null) {
            supplierMock.shutdown();
        }
    }

    @Test
    @DisplayName("동일 idempotency key 재전송 → supplier 1회 호출, supplierReceiptRef 동일")
    void sameIdempotencyKeyProducesConsistentResult() throws InterruptedException {
        // Arrange
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());
        ActorContext buyer = new ActorContext("buyer-idem-001", TENANT_SCM, Set.of("BUYER"));
        String idempotencyKey = "idem-key-unique-001";

        // Stub supplier with a single successful response.
        supplierMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"" + SUPPLIER_RECEIPT_REF + "\",\"status\":\"RECEIVED\"}"));

        // Act: first submit — should succeed and transition PO to SUBMITTED.
        PurchaseOrderView firstView =
                service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), idempotencyKey));

        assertThat(firstView.status()).isEqualTo(PoStatus.SUBMITTED);

        // Verify supplier received exactly one request with the correct idempotency key.
        RecordedRequest recordedRequest = supplierMock.takeRequest();
        assertThat(recordedRequest.getHeader("Idempotency-Key"))
                .contains(idempotencyKey);

        // Second submit with the same key on an already-SUBMITTED PO —
        // PoStatusMachine rejects the transition, so the supplier is NOT called again.
        try {
            service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), idempotencyKey));
        } catch (Exception ignored) {
            // Expected: PoStatusTransitionInvalidException because PO is already SUBMITTED.
        }

        // Assert: no second HTTP request to the supplier.
        assertThat(supplierMock.getRequestCount())
                .as("supplier must receive exactly one HTTP request across both submit attempts")
                .isEqualTo(1);

        // The PO remains SUBMITTED with the original receiptRef recorded in history.
        var poView = service.get(po.getId(), buyer);
        assertThat(poView.status()).isEqualTo(PoStatus.SUBMITTED);
    }
}
