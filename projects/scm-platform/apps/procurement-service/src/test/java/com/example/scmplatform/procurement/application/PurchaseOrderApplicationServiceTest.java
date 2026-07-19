package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.application.command.AcknowledgePurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.CancelPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ConfirmPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ReceiveAsnCommand;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import com.example.scmplatform.procurement.application.port.outbound.SupplierAdapterPort;
import com.example.scmplatform.procurement.domain.asn.AdvanceShipmentNotice;
import com.example.scmplatform.procurement.domain.asn.repository.AsnRepository;
import com.example.scmplatform.procurement.domain.audit.AuditLog;
import com.example.scmplatform.procurement.domain.audit.AuditLogRepository;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.error.SupplierInactiveException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderRepository;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.po.status.PoStatusHistoryRepository;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.domain.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests for {@link PurchaseOrderApplicationService}. Covers
 * the 6 use-case commands (draft / submit / acknowledge / confirm / cancel /
 * receiveAsn) at the orchestration layer — domain mechanics are covered in
 * {@link com.example.scmplatform.procurement.domain.po.status.PoStatusMachineTest}.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderApplicationServiceTest {

    private static final String TENANT = "scm";
    private static final String BUYER_ACCOUNT = "buyer-001";
    private static final String OPERATOR_ACCOUNT = "operator-001";
    private static final String SUPPLIER_ID = "sup-001";

    private static final ActorContext BUYER = new ActorContext(BUYER_ACCOUNT, TENANT, Set.of("BUYER"));
    private static final ActorContext OPERATOR = new ActorContext(OPERATOR_ACCOUNT, TENANT, Set.of("OPERATOR"));

    @Mock
    PurchaseOrderRepository poRepository;
    @Mock
    PoStatusHistoryRepository historyRepository;
    @Mock
    AsnRepository asnRepository;
    @Mock
    SupplierRepository supplierRepository;
    @Mock
    AuditLogRepository auditLogRepository;
    @Mock
    SupplierAdapterPort supplierAdapter;
    @Mock
    ProcurementEventPublisher eventPublisher;

    @InjectMocks
    PurchaseOrderApplicationService service;

    @BeforeEach
    void setUp() {
        // PO repo's save() is identity-style — JPA-friendly behaviour expected by the service.
        // lenient() because not every test path reaches save (e.g. SupplierNotFoundException early returns).
        lenient().when(poRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Supplier activeSupplier() {
        return Supplier.create(SUPPLIER_ID, TENANT, "Acme", SupplierStatus.ACTIVE);
    }

    private DraftPurchaseOrderCommand validDraftCommand() {
        return new DraftPurchaseOrderCommand(
                BUYER, SUPPLIER_ID, "USD",
                List.of(new DraftPurchaseOrderCommand.Line(
                        1, "sku-001", "sup-sku-001",
                        new BigDecimal("10"), new BigDecimal("5.00")))
        );
    }

    // ---------------- DRAFT ----------------

    @Test
    @DisplayName("draft() persists PO + audit + emits no event (DRAFT is private state)")
    void draftPersistsAndAudits() {
        when(supplierRepository.findById(SUPPLIER_ID, TENANT))
                .thenReturn(Optional.of(activeSupplier()));

        PurchaseOrderView view = service.draft(validDraftCommand());

        assertThat(view).isNotNull();
        assertThat(view.status()).isEqualTo(PoStatus.DRAFT);
        verify(poRepository, times(1)).save(any(PurchaseOrder.class));
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
        // DRAFT은 외부 가시성이 없으므로 outbox 발행 없음
        verify(eventPublisher, never()).publishPoSubmitted(any());
    }

    @Test
    @DisplayName("draft() throws SupplierNotFoundException when supplier missing")
    void draftSupplierNotFound() {
        when(supplierRepository.findById(SUPPLIER_ID, TENANT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.draft(validDraftCommand()))
                .isInstanceOf(SupplierNotFoundException.class);
        verify(poRepository, never()).save(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("draft() throws SupplierInactiveException for non-ACTIVE supplier")
    void draftSupplierInactive() {
        Supplier inactive = Supplier.create(SUPPLIER_ID, TENANT, "Acme", SupplierStatus.INACTIVE);
        when(supplierRepository.findById(SUPPLIER_ID, TENANT))
                .thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.draft(validDraftCommand()))
                .isInstanceOf(SupplierInactiveException.class);
        verify(poRepository, never()).save(any());
    }

    // ---------------- SUBMIT ----------------

    @Test
    @DisplayName("submit() calls supplier adapter, saves PO with SUBMITTED, writes history + audit + outbox")
    void submitHappyPath() {
        PurchaseOrder draft = freshDraftPo();
        when(poRepository.findById(draft.getId(), TENANT)).thenReturn(Optional.of(draft));
        when(supplierAdapter.submitPurchaseOrder(any(), eq("idem-001")))
                .thenReturn(new SupplierAdapterPort.SupplierSubmissionResult("RCPT-001", "RECEIVED"));

        PurchaseOrderView view = service.submit(
                new SubmitPurchaseOrderCommand(BUYER, draft.getId(), "idem-001"));

        assertThat(view.status()).isEqualTo(PoStatus.SUBMITTED);
        verify(supplierAdapter, times(1))
                .submitPurchaseOrder(any(), eq("idem-001"));
        verify(historyRepository, times(1)).save(any());
        verify(auditLogRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publishPoSubmitted(any());
    }

    @Test
    @DisplayName("submit() throws PoNotFoundException for unknown PO")
    void submitPoNotFound() {
        when(poRepository.findById("missing", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(
                new SubmitPurchaseOrderCommand(BUYER, "missing", "idem-001")))
                .isInstanceOf(PoNotFoundException.class);
        verify(supplierAdapter, never()).submitPurchaseOrder(any(), any());
    }

    @Test
    @DisplayName("submit() bubbles supplier adapter failure without state mutation (Edge Case #7)")
    void submitSupplierFailureRollsBack() {
        PurchaseOrder draft = freshDraftPo();
        when(poRepository.findById(draft.getId(), TENANT)).thenReturn(Optional.of(draft));
        when(supplierAdapter.submitPurchaseOrder(any(), any()))
                .thenThrow(new RuntimeException("circuit OPEN"));

        assertThatThrownBy(() -> service.submit(
                new SubmitPurchaseOrderCommand(BUYER, draft.getId(), "idem-001")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("circuit OPEN");
        verify(poRepository, never()).save(any());
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishPoSubmitted(any());
    }

    // ---------------- ACKNOWLEDGE (webhook) ----------------

    @Test
    @DisplayName("acknowledge() transitions SUBMITTED → ACKNOWLEDGED + emits event")
    void acknowledgeHappyPath() {
        PurchaseOrder po = freshDraftPo();
        po.submit(com.example.scmplatform.procurement.domain.po.status.ActorType.BUYER);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.acknowledge(new AcknowledgePurchaseOrderCommand(TENANT, po.getId(), "ACK-001"));

        verify(historyRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publishPoAcknowledged(any(), eq("ACK-001"));
    }

    @Test
    @DisplayName("acknowledge() is idempotent — already-ACKNOWLEDGED PO returns no-op (no extra event)")
    void acknowledgeIdempotent() {
        PurchaseOrder po = freshDraftPo();
        po.submit(com.example.scmplatform.procurement.domain.po.status.ActorType.BUYER);
        po.acknowledge(com.example.scmplatform.procurement.domain.po.status.ActorType.SUPPLIER);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.acknowledge(new AcknowledgePurchaseOrderCommand(TENANT, po.getId(), "ACK-002"));

        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishPoAcknowledged(any(), any());
    }

    // ---------------- CONFIRM ----------------

    @Test
    @DisplayName("confirm() requires OPERATOR — happy path issues CONFIRMED event")
    void confirmHappyPath() {
        PurchaseOrder po = acknowledgedPo();
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.confirm(new ConfirmPurchaseOrderCommand(OPERATOR, po.getId()));

        verify(historyRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publishPoConfirmed(any(), eq(OPERATOR_ACCOUNT));
    }

    @Test
    @DisplayName("confirm() throws PoNotFoundException for unknown PO")
    void confirmPoNotFound() {
        when(poRepository.findById("missing", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
                new ConfirmPurchaseOrderCommand(OPERATOR, "missing")))
                .isInstanceOf(PoNotFoundException.class);
    }

    // ---------------- CONFIRM → inbound-expected (ADR-MONO-050 D1/D2/D4) ----------------

    @Test
    @DisplayName("confirm() of a WMS_WAREHOUSE-addressed replenishment PO emits inbound-expected (ADR-050 D1)")
    void confirmWarehouseAddressedEmitsInboundExpected() {
        PurchaseOrder po = acknowledgedFromSuggestionPo("wh-01", "WMS_WAREHOUSE", 7);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.confirm(new ConfirmPurchaseOrderCommand(OPERATOR, po.getId()));

        verify(eventPublisher, times(1)).publishPoConfirmed(any(), eq(OPERATOR_ACCOUNT));
        verify(eventPublisher, times(1)).publishInboundExpected(any());
    }

    @Test
    @DisplayName("confirm() of an operator-authored PO (no destination) does NOT emit inbound-expected (fail-closed)")
    void confirmOperatorPoDoesNotEmitInboundExpected() {
        PurchaseOrder po = acknowledgedPo(); // createDraft → no destination
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.confirm(new ConfirmPurchaseOrderCommand(OPERATOR, po.getId()));

        verify(eventPublisher, times(1)).publishPoConfirmed(any(), eq(OPERATOR_ACCOUNT));
        verify(eventPublisher, never()).publishInboundExpected(any());
    }

    @Test
    @DisplayName("confirm() of a 3PL-destination PO does NOT emit inbound-expected (ADR-050 D4 producer filter)")
    void confirm3plDestinationDoesNotEmitInboundExpected() {
        PurchaseOrder po = acknowledgedFromSuggestionPo("3pl-node-01", "THIRD_PARTY_LOGISTICS", 7);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.confirm(new ConfirmPurchaseOrderCommand(OPERATOR, po.getId()));

        verify(eventPublisher, never()).publishInboundExpected(any());
    }

    @Test
    @DisplayName("confirm() of a warehouse PO with unknown lead time does NOT emit (fail-closed, Failure Scenario B)")
    void confirmWarehousePoWithNullLeadTimeDoesNotEmit() {
        PurchaseOrder po = acknowledgedFromSuggestionPo("wh-01", "WMS_WAREHOUSE", null);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.confirm(new ConfirmPurchaseOrderCommand(OPERATOR, po.getId()));

        verify(eventPublisher, never()).publishInboundExpected(any());
    }

    // ---------------- CANCEL ----------------

    @Test
    @DisplayName("cancel() with reason emits CANCELED event with reason payload")
    void cancelWithReason() {
        PurchaseOrder po = freshDraftPo();
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.cancel(new CancelPurchaseOrderCommand(BUYER, po.getId(), "out of budget"));

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventPublisher, times(1))
                .publishPoCanceled(any(), reasonCaptor.capture(), eq(BUYER_ACCOUNT));
        assertThat(reasonCaptor.getValue()).isEqualTo("out of budget");
    }

    @Test
    @DisplayName("cancel() with null reason emits event with empty reason payload (audit JSON safe)")
    void cancelNullReason() {
        PurchaseOrder po = freshDraftPo();
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.cancel(new CancelPurchaseOrderCommand(BUYER, po.getId(), null));

        verify(historyRepository, times(1)).save(any());
        verify(eventPublisher, times(1)).publishPoCanceled(any(), eq(null), eq(BUYER_ACCOUNT));
    }

    @Test
    @DisplayName("cancel() of a WMS_WAREHOUSE-addressed PO emits inbound-expected.cancelled (ADR-050 D6.3)")
    void cancelWarehouseAddressedEmitsInboundExpectedCancelled() {
        PurchaseOrder po = draftFromSuggestionPo("wh-01", "WMS_WAREHOUSE", 7);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.cancel(new CancelPurchaseOrderCommand(OPERATOR, po.getId(), "supplier delay"));

        verify(eventPublisher, times(1)).publishPoCanceled(any(), any(), eq(OPERATOR_ACCOUNT));
        verify(eventPublisher, times(1)).publishInboundExpectedCancelled(any());
    }

    @Test
    @DisplayName("cancel() of a CONFIRMED warehouse PO is now allowed and emits inbound-expected.cancelled (ADR-050 D6.3 / SCM-BE-036)")
    void cancelConfirmedWarehousePoEmitsInboundExpectedCancelled() {
        PurchaseOrder po = confirmedFromSuggestionPo("wh-01", "WMS_WAREHOUSE", 7);
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.cancel(new CancelPurchaseOrderCommand(OPERATOR, po.getId(), "confirmed order withdrawn"));

        // The now-reachable CONFIRMED→CANCELED transition succeeded (no exception) …
        assertThat(po.getStatus())
                .isEqualTo(com.example.scmplatform.procurement.domain.po.status.PoStatus.CANCELED);
        // … and both the generic cancel and the wms-directed cancellation fired — this
        // is the case that actually strands a wms expectation (cancel after confirm).
        verify(eventPublisher, times(1)).publishPoCanceled(any(), any(), eq(OPERATOR_ACCOUNT));
        verify(eventPublisher, times(1)).publishInboundExpectedCancelled(any());
    }

    @Test
    @DisplayName("cancel() of an operator-authored PO does NOT emit inbound-expected.cancelled")
    void cancelOperatorPoDoesNotEmitInboundExpectedCancelled() {
        PurchaseOrder po = freshDraftPo(); // createDraft → no destination
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        service.cancel(new CancelPurchaseOrderCommand(BUYER, po.getId(), null));

        verify(eventPublisher, never()).publishInboundExpectedCancelled(any());
    }

    // ---------------- RECEIVE ASN ----------------

    @Test
    @DisplayName("receiveAsn() with new supplierAsnRef saves ASN, applies PO transition, emits ASN event")
    void receiveAsnNewIsProcessed() {
        PurchaseOrder po = confirmedPo();
        String poLineId = po.getLines().iterator().next().getId();
        String supplierRef = "ASN-NEW";
        when(asnRepository.findBySupplierAsnRef(supplierRef, TENANT)).thenReturn(Optional.empty());
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));
        when(asnRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.receiveAsn(new ReceiveAsnCommand(
                TENANT, po.getId(), supplierRef, Instant.now(),
                List.of(new ReceiveAsnCommand.AsnLine(poLineId, new BigDecimal("10")))));

        verify(asnRepository, times(1)).save(any());
        verify(eventPublisher, times(1))
                .publishAsnReceived(any(), eq(po.getId()), eq(TENANT), eq(supplierRef), any(), any());
    }

    @Test
    @DisplayName("receiveAsn() with duplicate supplierAsnRef returns existing ASN (S2 idempotency)")
    void receiveAsnDuplicateIdempotent() {
        AdvanceShipmentNotice existing = AdvanceShipmentNotice.create(
                "asn-existing", TENANT, "po-001", "ASN-DUP", Instant.now());
        when(asnRepository.findBySupplierAsnRef("ASN-DUP", TENANT))
                .thenReturn(Optional.of(existing));

        AsnView view = service.receiveAsn(new ReceiveAsnCommand(
                TENANT, "po-001", "ASN-DUP", Instant.now(), List.of()));

        assertThat(view.id()).isEqualTo("asn-existing");
        verify(poRepository, never()).findById(any(), any());
        verify(asnRepository, never()).save(any());
        verify(eventPublisher, never()).publishAsnReceived(any(), any(), any(), any(), any(), any());
    }

    // ---------------- READS ----------------

    @Test
    @DisplayName("get() throws PoNotFoundException for unknown PO (cross-tenant misuse appears as not-found)")
    void getNotFound() {
        when(poRepository.findById("missing", TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("missing", BUYER))
                .isInstanceOf(PoNotFoundException.class);
    }

    @Test
    @DisplayName("get() returns view of existing PO")
    void getReturnsView() {
        PurchaseOrder po = freshDraftPo();
        when(poRepository.findById(po.getId(), TENANT)).thenReturn(Optional.of(po));

        PurchaseOrderView view = service.get(po.getId(), BUYER);

        assertThat(view.id()).isEqualTo(po.getId());
        assertThat(view.status()).isEqualTo(PoStatus.DRAFT);
    }

    // ---------------- SEARCH (sort building relocated to PageRequests — see PageRequestsTest) ----------------

    @Test
    @DisplayName("search() forwards the PageQuery (incl. sort) to the port and maps the result")
    void searchForwardsPageQueryAndMapsResult() {
        PageQuery pageQuery = PageQuery.of(0, 10, "createdAt", "desc");
        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        when(poRepository.search(eq(TENANT), any(), any(), captor.capture()))
                .thenReturn(new PageResult<>(List.of(), 0, 10, 0, 0));

        PageResult<PurchaseOrderView> result = service.search(BUYER, null, null, pageQuery);

        // The service forwards sort as data on the PageQuery; the PageQuery -> Spring Sort
        // mapping is covered Docker-free by PageRequestsTest.
        assertThat(captor.getValue().sortBy()).isEqualTo("createdAt");
        assertThat(captor.getValue().sortDirection()).isEqualTo("desc");
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(10);
    }

    // ---------------- helpers ----------------

    private PurchaseOrder freshDraftPo() {
        PurchaseOrder po = PurchaseOrder.createDraft(
                "po-001", TENANT, "PO-TEST-001", SUPPLIER_ID, BUYER_ACCOUNT, "USD");
        po.addLine(com.example.scmplatform.procurement.domain.po.PurchaseOrderLine.create(
                "line-001", po.getId(), TENANT, 1, "sku-001", "sup-sku-001",
                new BigDecimal("10"), new BigDecimal("5.00")));
        return po;
    }

    private PurchaseOrder acknowledgedPo() {
        PurchaseOrder po = freshDraftPo();
        po.submit(com.example.scmplatform.procurement.domain.po.status.ActorType.BUYER);
        po.acknowledge(com.example.scmplatform.procurement.domain.po.status.ActorType.SUPPLIER);
        return po;
    }

    private PurchaseOrder confirmedPo() {
        PurchaseOrder po = acknowledgedPo();
        po.confirm(com.example.scmplatform.procurement.domain.po.status.ActorType.OPERATOR);
        return po;
    }

    private PurchaseOrder draftFromSuggestionPo(String warehouseId, String nodeType, Integer leadTimeDays) {
        PurchaseOrder po = PurchaseOrder.createDraftFromSuggestion(
                "po-inb-001", TENANT, "PO-INB-001", SUPPLIER_ID, OPERATOR_ACCOUNT, "KRW",
                "sugg-001", warehouseId, nodeType, leadTimeDays);
        po.addLine(com.example.scmplatform.procurement.domain.po.PurchaseOrderLine.create(
                "line-inb-001", po.getId(), TENANT, 1, "SKU-APPLE-001", null,
                new BigDecimal("100"), BigDecimal.ZERO));
        return po;
    }

    private PurchaseOrder acknowledgedFromSuggestionPo(String warehouseId, String nodeType, Integer leadTimeDays) {
        PurchaseOrder po = draftFromSuggestionPo(warehouseId, nodeType, leadTimeDays);
        po.submit(com.example.scmplatform.procurement.domain.po.status.ActorType.BUYER);
        po.acknowledge(com.example.scmplatform.procurement.domain.po.status.ActorType.SUPPLIER);
        return po;
    }

    private PurchaseOrder confirmedFromSuggestionPo(String warehouseId, String nodeType, Integer leadTimeDays) {
        PurchaseOrder po = acknowledgedFromSuggestionPo(warehouseId, nodeType, leadTimeDays);
        po.confirm(com.example.scmplatform.procurement.domain.po.status.ActorType.OPERATOR);
        return po;
    }
}
