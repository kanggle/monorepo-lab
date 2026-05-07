package com.example.scmplatform.procurement.integration;

import com.example.messaging.outbox.OutboxJpaRepository;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.PoStatusHistoryJpaRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * IT-5: State machine atomicity.
 *
 * <p>When the outbox writer throws an exception after the supplier call but
 * before the transaction commits, the entire unit of work — PO state
 * transition, status history row, and outbox row — must be rolled back.
 * The PO must remain in DRAFT status (atomicity invariant from
 * rules/traits/transactional.md T3 + T4).
 *
 * <p>Approach: spy on {@link OutboxJpaRepository} and force its {@code save}
 * to throw a {@link RuntimeException} on first invocation. The application
 * service wraps everything in a single {@code @Transactional} boundary so
 * the DB rollback restores DRAFT status + removes any partially-written rows.
 */
@Tag("integration")
@DisplayName("IT-5: State machine atomicity — outbox failure rolls back PO + history")
class StateMachineAtomicityIntegrationTest extends AbstractProcurementIntegrationTest {

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

    @MockitoSpyBean
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private PoStatusHistoryJpaRepository historyJpa;

    @AfterEach
    void resetMock() throws IOException {
        Mockito.reset(outboxJpaRepository);
        if (supplierMock != null) {
            supplierMock.shutdown();
        }
    }

    @Test
    @DisplayName("outbox writer 실패 → 트랜잭션 롤백 → PO=DRAFT, history=없음, outbox=없음")
    void outboxWriteFailureRollsBackEntireTransaction() {
        // Arrange: supplier returns success so the call itself passes.
        supplierMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"RCPT-ATM-001\",\"status\":\"RECEIVED\"}"));

        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());
        ActorContext buyer = new ActorContext("buyer-atm-001", TENANT_SCM, Set.of("BUYER"));

        // Force outbox repository to throw on save — simulating an outbox write failure.
        doThrow(new RuntimeException("outbox write failure — simulated"))
                .when(outboxJpaRepository).save(any());

        // Act: submit must fail because outbox write throws inside the transaction.
        assertThatThrownBy(() ->
                service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), "idem-atm-001")))
                .isInstanceOf(RuntimeException.class);

        // Assert: PO rolled back to DRAFT (transaction was not committed).
        var rolledBackPo = poJpa.findByIdAndTenantId(po.getId(), TENANT_SCM);
        assertThat(rolledBackPo).isPresent();
        assertThat(rolledBackPo.get().getStatus())
                .as("PO must remain DRAFT after transaction rollback")
                .isEqualTo(PoStatus.DRAFT);

        // Assert: no status history row was written (rolled back).
        long historyCount = historyJpa.findAll().stream()
                .filter(h -> h.getPoId().equals(po.getId()))
                .count();
        assertThat(historyCount)
                .as("no po_status_history row must survive the rollback")
                .isZero();

        // Assert: audit log for SUBMIT must not exist (rolled back).
        List<AuditLog> auditRows = auditLogJpa.findAll().stream()
                .filter(a -> a.getAggregateId().equals(po.getId())
                        && "SUBMIT".equals(a.getAction()))
                .toList();
        assertThat(auditRows)
                .as("no SUBMIT audit log must survive the rollback")
                .isEmpty();
    }
}
