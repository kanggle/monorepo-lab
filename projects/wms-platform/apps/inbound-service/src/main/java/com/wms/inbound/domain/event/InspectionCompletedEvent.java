package com.wms.inbound.domain.event;

import com.wms.inbound.domain.model.Inspection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionCompletedEvent(
        UUID inspectionId,
        UUID asnId,
        String asnNo,
        UUID warehouseId,
        String inspectorId,
        Instant completedAt,
        List<Line> lines,
        int discrepancyCount,
        List<DiscrepancySummary> discrepancySummary,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    public record Line(
            UUID inspectionLineId,
            UUID asnLineId,
            UUID skuId,
            UUID lotId,
            String lotNo,
            int expectedQty,
            int qtyPassed,
            int qtyDamaged,
            int qtyShort
    ) {}

    public record DiscrepancySummary(
            UUID discrepancyId,
            UUID asnLineId,
            String discrepancyType,
            int variance,
            boolean acknowledged
    ) {}

    /**
     * Factory: build an {@link InspectionCompletedEvent} from an {@link Inspection} aggregate.
     *
     * <p>The two call sites differ in how they compute {@code discrepancyCount}:
     * <ul>
     *   <li>{@code InspectionService} — passes the actual count of unacknowledged discrepancies
     *       (computed at record time when all discrepancies are immediately acknowledged).
     *   <li>{@code AcknowledgeDiscrepancyService} — always passes {@code 0} because by the time
     *       the event fires all discrepancies have been acknowledged (so unacked count is zero).
     * </ul>
     * <p><strong>Note:</strong> the two values should converge to the same result in correct
     * operation, but the divergence is preserved here as-is pending a dedicated follow-up
     * to verify and unify the logic.
     *
     * @param inspection       the completed inspection aggregate
     * @param asnNo            ASN number carried in the event payload
     * @param warehouseId      warehouse the ASN belongs to
     * @param discrepancyCount number of (unacknowledged) discrepancies — caller decides semantics
     * @param occurredAt       event timestamp
     * @param actorId          actor who triggered completion
     */
    public static InspectionCompletedEvent fromInspection(
            Inspection inspection,
            String asnNo,
            UUID warehouseId,
            int discrepancyCount,
            Instant occurredAt,
            String actorId) {
        List<Line> lines = inspection.getLines().stream()
                .map(l -> new Line(
                        l.getId(), l.getAsnLineId(), l.getSkuId(), l.getLotId(), l.getLotNo(),
                        0, l.getQtyPassed(), l.getQtyDamaged(), l.getQtyShort()))
                .toList();
        List<DiscrepancySummary> discSummary = inspection.getDiscrepancies().stream()
                .map(d -> new DiscrepancySummary(
                        d.getId(), d.getAsnLineId(),
                        d.getDiscrepancyType().name(), d.getVariance(), d.isAcknowledged()))
                .toList();
        return new InspectionCompletedEvent(
                inspection.getId(), inspection.getAsnId(), asnNo, warehouseId,
                inspection.getInspectorId(), inspection.getCompletedAt(),
                lines, discrepancyCount, discSummary, occurredAt, actorId);
    }

    @Override
    public UUID aggregateId() { return inspectionId; }

    @Override
    public String aggregateType() { return "inspection"; }

    @Override
    public String eventType() { return "inbound.inspection.completed"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
