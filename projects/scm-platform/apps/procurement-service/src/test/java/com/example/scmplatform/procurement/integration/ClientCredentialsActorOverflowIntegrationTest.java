package com.example.scmplatform.procurement.integration;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.CancelPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.ConfirmPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.command.DraftFromSuggestionCommand;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.application.event.ProcurementEventPublisher;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.PoStatusHistoryJpaRepository;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.ProcurementOutboxJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT (Testcontainers + real Postgres): TASK-SCM-BE-050 — a client-credentials
 * caller whose {@code accountId} is the 37-char
 * {@code scm-platform-internal-services-client} subject can now draft, cancel and
 * confirm POs without overflowing any {@code VARCHAR} actor column, and the stored
 * value round-trips as the full, verifiable {@code sub} (never truncated).
 *
 * <p>Before the V5 widening this class's writes failed on flush with
 * {@code value too long for type character varying(36)} → 500.
 *
 * <p>Proves what the Docker-free {@code :check} lane cannot — the three widened
 * columns against a live PostgreSQL:
 * <ul>
 *   <li>AC-1: {@code purchase_orders.buyer_account_id} (the hard-failing column);</li>
 *   <li>AC-2: {@code audit_log.actor_account_id} and
 *       {@code po_status_history.actor_account_id} (the two sibling columns);</li>
 *   <li>AC-3: the TASK-SCM-BE-048→049 chain — a {@code THIRD_PARTY_LOGISTICS} PO
 *       drafted by the client-credentials actor reaches CONFIRMED and BE-049's
 *       honour-sink producer persists the
 *       {@code scm.procurement.inbound-expected.third-party} outbox row (the step
 *       that was blocked because the draft 500'd before routing could act on a
 *       persisted PO).</li>
 * </ul>
 */
@Tag("integration")
@DisplayName("IT: client-credentials 37-char actor id no longer overflows (TASK-SCM-BE-050)")
class ClientCredentialsActorOverflowIntegrationTest extends AbstractProcurementIntegrationTest {

    /** scm's documented machine caller — sub == client_id, 37 chars (iam-integration.md E1). */
    private static final String CLIENT_CREDENTIALS_SUB = "scm-platform-internal-services-client";

    /** No roles claim on a client-credentials token → maps to BUYER. */
    private static final ActorContext CLIENT_CREDENTIALS_ACTOR =
            new ActorContext(CLIENT_CREDENTIALS_SUB, TENANT_SCM, Set.of());

    /** A human operator confirms ACKNOWLEDGED→CONFIRMED (state machine: BUYER cannot). */
    private static final ActorContext OPERATOR =
            new ActorContext("operator-console-001", TENANT_SCM, Set.of("OPERATOR"));

    @Autowired
    private PurchaseOrderApplicationService service;

    @Autowired
    private PoStatusHistoryJpaRepository historyJpa;

    @Autowired
    private ProcurementOutboxJpaRepository outboxJpa;

    @Test
    @DisplayName("AC-1/AC-2: plain draft by the 37-char actor round-trips buyer_account_id + audit_log actor")
    void draftByClientCredentialsActorRoundTrips() {
        Supplier supplier = persistActiveSupplier(TENANT_SCM);

        DraftPurchaseOrderCommand cmd = new DraftPurchaseOrderCommand(
                CLIENT_CREDENTIALS_ACTOR,
                supplier.getId(),
                "KRW",
                List.of(new DraftPurchaseOrderCommand.Line(
                        1, "SKU-A", "SUP-A", new BigDecimal("10"), new BigDecimal("5.00"))));

        PurchaseOrderView view = service.draft(cmd);

        // buyer_account_id — the column that used to 500 — holds the full 37-char sub.
        PurchaseOrder reloaded = poJpa.findById(view.id()).orElseThrow();
        assertThat(reloaded.getBuyerAccountId())
                .as("buyer_account_id must be the full, verifiable client-credentials sub")
                .isEqualTo(CLIENT_CREDENTIALS_SUB)
                .hasSize(37);

        // audit_log.actor_account_id sibling — the DRAFT row also carries the full id.
        boolean auditRoundTrips = auditLogJpa.findAll().stream()
                .filter(a -> view.id().equals(a.getAggregateId()) && "DRAFT".equals(a.getAction()))
                .anyMatch(a -> CLIENT_CREDENTIALS_SUB.equals(a.getActorAccountId()));
        assertThat(auditRoundTrips)
                .as("audit_log DRAFT row round-trips the full 37-char actor id")
                .isTrue();
    }

    @Test
    @DisplayName("AC-2: cancel by the 37-char actor round-trips po_status_history.actor_account_id")
    void cancelByClientCredentialsActorRoundTripsHistory() {
        String suggestionId = UuidV7.randomString();
        String nodeId = UuidV7.randomString();
        DraftFromSuggestionCommand draftCmd = new DraftFromSuggestionCommand(
                CLIENT_CREDENTIALS_ACTOR,
                UuidV7.randomString(),                 // unpersisted supplier (FK-free from-suggestion path)
                "KRW",
                suggestionId,
                nodeId,
                PurchaseOrder.NODE_TYPE_THIRD_PARTY_LOGISTICS,
                7,
                List.of(new DraftFromSuggestionCommand.Line(1, "SKU-B", 50, "LAST_KNOWN")));

        PurchaseOrderView drafted = service.draftFromSuggestion(draftCmd);

        // DRAFT → CANCELED is a BUYER-legal transition the machine caller can drive,
        // and it writes a po_status_history row carrying actor_account_id.
        service.cancel(new CancelPurchaseOrderCommand(
                CLIENT_CREDENTIALS_ACTOR, drafted.id(), "aborted by machine caller"));

        boolean historyRoundTrips = historyJpa.findAll().stream()
                .filter(h -> drafted.id().equals(h.getPoId()) && h.getToStatus() == PoStatus.CANCELED)
                .anyMatch(h -> CLIENT_CREDENTIALS_SUB.equals(h.getActorAccountId()));
        assertThat(historyRoundTrips)
                .as("po_status_history CANCELED row round-trips the full 37-char actor id")
                .isTrue();
    }

    @Test
    @DisplayName("AC-3: BE-048→049 — 3PL PO drafted by the 37-char actor confirms and emits the honour-sink event")
    void thirdPartyChainReachesHonourSinkProducer() {
        String suggestionId = UuidV7.randomString();
        String nodeId = UuidV7.randomString();

        // The PO the machine caller drafts (buyer_account_id = 37-char sub), pre-advanced
        // to ACKNOWLEDGED via the domain state machine so an OPERATOR can CONFIRM it.
        String poId = UuidV7.randomString();
        String poNumber = "PO-IT-" + poId.replace("-", "").substring(20).toUpperCase();
        PurchaseOrder po = PurchaseOrder.createDraftFromSuggestion(
                poId, TENANT_SCM, poNumber,
                UuidV7.randomString(), CLIENT_CREDENTIALS_SUB, "KRW",
                suggestionId, nodeId, PurchaseOrder.NODE_TYPE_THIRD_PARTY_LOGISTICS, 7);
        PurchaseOrderLine line = PurchaseOrderLine.create(
                UuidV7.randomString(), poId, TENANT_SCM,
                1, "SKU-3PL", "SUP-3PL", new BigDecimal("30"), new BigDecimal("2.00"));
        po.addLine(line);
        po.submit(ActorType.OPERATOR);
        po.acknowledge(ActorType.SUPPLIER);
        poJpa.save(po);
        lineJpa.save(line);

        // Operator confirms → BE-049 honour-sink producer fires (was unreachable while
        // the draft 500'd). CONFIRM writes po_status_history + audit_log actor rows too.
        service.confirm(new ConfirmPurchaseOrderCommand(OPERATOR, poId));

        PurchaseOrder confirmed = poJpa.findById(poId).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(PoStatus.CONFIRMED);
        // buyer_account_id set by the machine caller at draft survives intact.
        assertThat(confirmed.getBuyerAccountId()).isEqualTo(CLIENT_CREDENTIALS_SUB);

        // The originally-blocked outcome: the 3PL honour-sink event is persisted.
        boolean honourSinkEmitted = outboxJpa.findAll().stream()
                .anyMatch(e -> ProcurementEventPublisher.EVENT_INBOUND_EXPECTED_THIRD_PARTY.equals(e.getEventType())
                        && poId.equals(e.getAggregateId()));
        assertThat(honourSinkEmitted)
                .as("BE-049 honour-sink producer persists scm.procurement.inbound-expected.third-party "
                        + "for the client-credentials-drafted 3PL PO")
                .isTrue();

        // CONFIRMED transition history row present (operator actor round-trips).
        boolean confirmHistory = historyJpa.findAll().stream()
                .filter(h -> poId.equals(h.getPoId()) && h.getToStatus() == PoStatus.CONFIRMED)
                .anyMatch(h -> "operator-console-001".equals(h.getActorAccountId()));
        assertThat(confirmHistory)
                .as("po_status_history CONFIRMED row recorded for the confirm actor")
                .isTrue();
    }
}
