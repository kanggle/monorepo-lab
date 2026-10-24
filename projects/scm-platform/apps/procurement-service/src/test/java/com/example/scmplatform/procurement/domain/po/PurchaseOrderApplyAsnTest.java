package com.example.scmplatform.procurement.domain.po;

import com.example.scmplatform.procurement.domain.error.AsnOverreceiptException;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-SCM-BE-063 — {@link PurchaseOrder#applyAsn} goes through {@code PoStatusMachine}'s
 * SYSTEM rules. Before the fix a full ASN set {@code RECEIVED} from ANY status (incl.
 * {@code CANCELED}) and a partial ASN silently changed line quantities on a not-yet-confirmed
 * PO. The control group ({@code CONFIRMED} / {@code PARTIALLY_RECEIVED}) must keep working.
 */
class PurchaseOrderApplyAsnTest {

    private static final String TENANT = "scm";

    /** Two lines: L1 qty 10, L2 qty 4. */
    private static PurchaseOrder draft() {
        PurchaseOrder po = PurchaseOrder.createDraft("po-1", TENANT, "PO-1", "sup-1", "buyer-1", "KRW");
        po.addLine(PurchaseOrderLine.create("L1", "po-1", TENANT, 1, "sku-1", null,
                new BigDecimal("10"), new BigDecimal("1.00")));
        po.addLine(PurchaseOrderLine.create("L2", "po-1", TENANT, 2, "sku-2", null,
                new BigDecimal("4"), new BigDecimal("1.00")));
        return po;
    }

    private static PurchaseOrder inStatus(PoStatus status) {
        PurchaseOrder po = draft();
        switch (status) {
            case DRAFT -> { }
            case SUBMITTED -> po.submit(ActorType.BUYER);
            case ACKNOWLEDGED -> {
                po.submit(ActorType.BUYER);
                po.acknowledge(ActorType.SUPPLIER);
            }
            case CONFIRMED -> confirm(po);
            case PARTIALLY_RECEIVED -> {
                confirm(po);
                po.applyAsn(List.of(new AsnLineReceipt("L1", new BigDecimal("3"))));
            }
            case RECEIVED -> {
                confirm(po);
                po.applyAsn(full());
            }
            default -> throw new IllegalArgumentException("not reachable in v1: " + status);
        }
        assertThat(po.getStatus()).isEqualTo(status);
        return po;
    }

    private static void confirm(PurchaseOrder po) {
        po.submit(ActorType.BUYER);
        po.acknowledge(ActorType.SUPPLIER);
        po.confirm(ActorType.OPERATOR);
    }

    private static PurchaseOrder canceledFrom(PoStatus before) {
        PurchaseOrder po = inStatus(before);
        po.cancel(ActorType.BUYER, "test");
        return po;
    }

    private static List<AsnLineReceipt> full() {
        return List.of(new AsnLineReceipt("L1", new BigDecimal("10")),
                new AsnLineReceipt("L2", new BigDecimal("4")));
    }

    private static List<AsnLineReceipt> partial() {
        return List.of(new AsnLineReceipt("L1", new BigDecimal("2")));
    }

    private static BigDecimal received(PurchaseOrder po, String lineId) {
        return po.linesView().stream().filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow().getReceivedQuantity();
    }

    private static void assertRejectedUnchanged(PurchaseOrder po, List<AsnLineReceipt> asn,
                                                PoStatus from, PoStatus to) {
        BigDecimal l1 = received(po, "L1");
        BigDecimal l2 = received(po, "L2");
        assertThatThrownBy(() -> po.applyAsn(asn))
                .isInstanceOfSatisfying(PoStatusTransitionInvalidException.class, e -> {
                    assertThat(e.getFrom()).isEqualTo(from);
                    assertThat(e.getTo()).isEqualTo(to);
                    assertThat(e.getActor()).isEqualTo(ActorType.SYSTEM);
                });
        assertThat(po.getStatus()).isEqualTo(from);
        assertThat(received(po, "L1")).isEqualByComparingTo(l1);
        assertThat(received(po, "L2")).isEqualByComparingTo(l2);
    }

    // ---- 🔴 AC-1: rejected statuses -------------------------------------------------

    @ParameterizedTest(name = "full ASN on {0} → 422 {0}→RECEIVED, nothing changes")
    @EnumSource(value = PoStatus.class, names = {"DRAFT", "SUBMITTED", "ACKNOWLEDGED"})
    void fullAsnBeforeConfirmIsRejected(PoStatus status) {
        assertRejectedUnchanged(inStatus(status), full(), status, PoStatus.RECEIVED);
    }

    @ParameterizedTest(name = "partial ASN on {0} → 422 {0}→PARTIALLY_RECEIVED, nothing changes")
    @EnumSource(value = PoStatus.class, names = {"DRAFT", "SUBMITTED", "ACKNOWLEDGED"})
    void partialAsnBeforeConfirmIsRejected(PoStatus status) {
        assertRejectedUnchanged(inStatus(status), partial(), status, PoStatus.PARTIALLY_RECEIVED);
    }

    @ParameterizedTest(name = "CANCELED (from {0}) — full ASN → 422 CANCELED→RECEIVED, nothing changes")
    @EnumSource(value = PoStatus.class, names = {"DRAFT", "SUBMITTED", "ACKNOWLEDGED", "CONFIRMED"})
    void fullAsnOnCanceledIsRejected(PoStatus before) {
        assertRejectedUnchanged(canceledFrom(before), full(), PoStatus.CANCELED, PoStatus.RECEIVED);
    }

    @Test
    @DisplayName("CANCELED — partial ASN → 422 CANCELED→PARTIALLY_RECEIVED, nothing changes")
    void partialAsnOnCanceledIsRejected() {
        assertRejectedUnchanged(canceledFrom(PoStatus.CONFIRMED), partial(),
                PoStatus.CANCELED, PoStatus.PARTIALLY_RECEIVED);
    }

    // ---- control group: legal statuses still progress ------------------------------

    @Test
    @DisplayName("control: CONFIRMED + full multi-line ASN → RECEIVED in ONE step (returns CONFIRMED)")
    void confirmedFullGoesStraightToReceived() {
        PurchaseOrder po = inStatus(PoStatus.CONFIRMED);

        PoStatus previous = po.applyAsn(full());

        assertThat(previous).isEqualTo(PoStatus.CONFIRMED);
        assertThat(po.getStatus()).isEqualTo(PoStatus.RECEIVED);
        assertThat(received(po, "L1")).isEqualByComparingTo("10");
        assertThat(received(po, "L2")).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("control: CONFIRMED + partial ASN → PARTIALLY_RECEIVED")
    void confirmedPartialGoesToPartiallyReceived() {
        PurchaseOrder po = inStatus(PoStatus.CONFIRMED);

        assertThat(po.applyAsn(partial())).isEqualTo(PoStatus.CONFIRMED);
        assertThat(po.getStatus()).isEqualTo(PoStatus.PARTIALLY_RECEIVED);
        assertThat(received(po, "L1")).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("control: PARTIALLY_RECEIVED + another partial → stays, returns null (no transition)")
    void partiallyReceivedFurtherPartialIsNoTransition() {
        PurchaseOrder po = inStatus(PoStatus.PARTIALLY_RECEIVED);

        assertThat(po.applyAsn(partial())).isNull();
        assertThat(po.getStatus()).isEqualTo(PoStatus.PARTIALLY_RECEIVED);
        assertThat(received(po, "L1")).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("control: PARTIALLY_RECEIVED + remainder → RECEIVED (returns PARTIALLY_RECEIVED)")
    void partiallyReceivedRemainderCompletes() {
        PurchaseOrder po = inStatus(PoStatus.PARTIALLY_RECEIVED); // L1 = 3

        PoStatus previous = po.applyAsn(List.of(new AsnLineReceipt("L1", new BigDecimal("7")),
                new AsnLineReceipt("L2", new BigDecimal("4"))));

        assertThat(previous).isEqualTo(PoStatus.PARTIALLY_RECEIVED);
        assertThat(po.getStatus()).isEqualTo(PoStatus.RECEIVED);
    }

    // ---- unchanged answers + all-or-nothing ------------------------------------------

    @Test
    @DisplayName("RECEIVED + any ASN → still ASN_OVERRECEIPT (line check runs before the status check)")
    void receivedStillAnswersOverreceipt() {
        PurchaseOrder po = inStatus(PoStatus.RECEIVED);

        assertThatThrownBy(() -> po.applyAsn(partial())).isInstanceOf(AsnOverreceiptException.class)
                .hasMessageContaining("ASN_OVERRECEIPT");
        assertThat(po.getStatus()).isEqualTo(PoStatus.RECEIVED);
    }

    @Test
    @DisplayName("CANCELED + over-ordered quantity → ASN_OVERRECEIPT, as before the fix")
    void canceledOverreceiptKeepsItsCode() {
        PurchaseOrder po = canceledFrom(PoStatus.CONFIRMED);

        assertThatThrownBy(() -> po.applyAsn(List.of(new AsnLineReceipt("L1", new BigDecimal("11")))))
                .isInstanceOf(AsnOverreceiptException.class);
    }

    @Test
    @DisplayName("all-or-nothing: line 2 over-receipts → line 1 is NOT applied either")
    void secondLineFailureLeavesFirstLineUntouched() {
        PurchaseOrder po = inStatus(PoStatus.CONFIRMED);

        assertThatThrownBy(() -> po.applyAsn(List.of(new AsnLineReceipt("L1", new BigDecimal("5")),
                new AsnLineReceipt("L2", new BigDecimal("5")))))
                .isInstanceOf(AsnOverreceiptException.class);

        assertThat(po.getStatus()).isEqualTo(PoStatus.CONFIRMED);
        assertThat(received(po, "L1")).isEqualByComparingTo("0");
        assertThat(received(po, "L2")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the same line twice in one ASN is summed: 6 + 4 = 10 fits; 6 + 5 over-receipts")
    void repeatedLineIsSummed() {
        PurchaseOrder ok = inStatus(PoStatus.CONFIRMED);
        ok.applyAsn(List.of(new AsnLineReceipt("L1", new BigDecimal("6")),
                new AsnLineReceipt("L1", new BigDecimal("4"))));
        assertThat(received(ok, "L1")).isEqualByComparingTo("10");
        assertThat(ok.getStatus()).isEqualTo(PoStatus.PARTIALLY_RECEIVED);

        PurchaseOrder over = inStatus(PoStatus.CONFIRMED);
        assertThatThrownBy(() -> over.applyAsn(List.of(new AsnLineReceipt("L1", new BigDecimal("6")),
                new AsnLineReceipt("L1", new BigDecimal("5")))))
                .isInstanceOf(AsnOverreceiptException.class)
                .hasMessageContaining("received 11");
        assertThat(received(over, "L1")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("unknown PO line → IllegalArgumentException, nothing applied")
    void unknownLine() {
        PurchaseOrder po = inStatus(PoStatus.CONFIRMED);

        assertThatThrownBy(() -> po.applyAsn(List.of(new AsnLineReceipt("L1", BigDecimal.ONE),
                new AsnLineReceipt("nope", BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PO line not found");
        assertThat(received(po, "L1")).isEqualByComparingTo("0");
    }
}
