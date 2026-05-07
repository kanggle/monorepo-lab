package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-4: Supplier submit idempotency.
 *
 * <p>The application service always calls the supplier adapter BEFORE the
 * domain state machine (Edge Case #7: circuit-open protection). This means
 * that when a buyer retransmits the same {@code Idempotency-Key}, the supplier
 * receives the HTTP request again and must handle idempotency at its own level
 * (returning the same {@code receiptRef}).
 *
 * <p>What the application guarantees:
 * <ul>
 *   <li>The {@code Idempotency-Key} header is forwarded to the supplier on
 *       every call so the supplier can deduplicate.</li>
 *   <li>After a successful first submit (PO → SUBMITTED), a retry with the
 *       same key will fail at the state machine ({@code SUBMITTED → SUBMITTED}
 *       is an invalid transition) before the transaction can commit, leaving
 *       the PO in {@code SUBMITTED} status with the original
 *       {@code supplierReceiptRef}.</li>
 * </ul>
 *
 * <p>Scenario:
 * <ol>
 *   <li>First submit: supplier returns 200 with {@code receiptRef}; PO → SUBMITTED.</li>
 *   <li>Second submit (same key): supplier is called again (returns same
 *       {@code receiptRef}), but {@link com.example.scmplatform.procurement.domain.po.status.PoStatusMachine}
 *       rejects {@code SUBMITTED → SUBMITTED}; call throws
 *       {@link PoStatusTransitionInvalidException}.</li>
 *   <li>PO remains SUBMITTED; supplier received exactly 2 HTTP requests.</li>
 * </ol>
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
    @DisplayName("동일 idempotency key 재전송: 공급사는 2회 호출 + 상태 머신 거부 → PO=SUBMITTED 유지")
    void sameIdempotencyKeyProducesConsistentResult() throws InterruptedException {
        // Arrange
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());
        ActorContext buyer = new ActorContext("buyer-idem-001", TENANT_SCM, Set.of("BUYER"));
        String idempotencyKey = "idem-key-unique-001";

        // Stub supplier with two successful responses sharing the same receiptRef.
        // The supplier handles idempotency on its side (returns the same ref for the same key).
        for (int i = 0; i < 2; i++) {
            supplierMock.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"receiptRef\":\"" + SUPPLIER_RECEIPT_REF + "\",\"status\":\"RECEIVED\"}"));
        }

        // Act: first submit — should succeed and transition PO to SUBMITTED.
        PurchaseOrderView firstView =
                service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), idempotencyKey));

        assertThat(firstView.status()).isEqualTo(PoStatus.SUBMITTED);

        // Verify first supplier request carries the idempotency key header.
        RecordedRequest firstRequest = supplierMock.takeRequest();
        assertThat(firstRequest.getHeader("Idempotency-Key"))
                .as("Idempotency-Key header must be forwarded to the supplier")
                .contains(idempotencyKey);

        // Second submit with the same key: supplier is called again (forwards
        // idempotency key for supplier-side deduplication), but the domain
        // state machine rejects SUBMITTED → SUBMITTED.
        assertThatThrownBy(() ->
                service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), idempotencyKey)))
                .isInstanceOf(PoStatusTransitionInvalidException.class);

        // Verify the second supplier request also carried the idempotency key.
        RecordedRequest secondRequest = supplierMock.takeRequest();
        assertThat(secondRequest.getHeader("Idempotency-Key"))
                .as("Idempotency-Key header must be forwarded on retry too")
                .contains(idempotencyKey);

        // The PO remains SUBMITTED — the failed second call did not corrupt state.
        PurchaseOrderView poView = service.get(po.getId(), buyer);
        assertThat(poView.status())
                .as("PO must remain SUBMITTED after the rejected retry")
                .isEqualTo(PoStatus.SUBMITTED);

        // Exactly 2 HTTP requests reached the supplier (application always
        // calls supplier before state machine check; supplier deduplicates by key).
        assertThat(supplierMock.getRequestCount())
                .as("supplier must receive exactly 2 HTTP requests (both retransmissions forwarded)")
                .isEqualTo(2);
    }
}
