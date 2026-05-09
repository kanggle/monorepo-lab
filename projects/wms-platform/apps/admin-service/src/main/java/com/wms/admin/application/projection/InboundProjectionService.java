package com.wms.admin.application.projection;

import static com.wms.admin.application.projection.PayloadJson.optionalDate;
import static com.wms.admin.application.projection.PayloadJson.optionalInstant;
import static com.wms.admin.application.projection.PayloadJson.optionalInteger;
import static com.wms.admin.application.projection.PayloadJson.optionalText;
import static com.wms.admin.application.projection.PayloadJson.optionalUuid;
import static com.wms.admin.application.projection.PayloadJson.requireArray;
import static com.wms.admin.application.projection.PayloadJson.text;
import static com.wms.admin.application.projection.PayloadJson.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.admin.application.port.AdminEventDedupePort;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.inbound.AsnSummaryEntity;
import com.wms.admin.readmodel.inbound.AsnSummaryRepository;
import com.wms.admin.readmodel.inbound.InspectionSummaryEntity;
import com.wms.admin.readmodel.inbound.InspectionSummaryRepository;
import com.wms.admin.readmodel.master.PartnerRefEntity;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects {@code wms.inbound.*} into:
 *
 * <ul>
 *   <li>{@code admin_asn_summary} — from {@code inbound.asn.received|cancelled|closed}</li>
 *   <li>{@code admin_inspection_summary} — from {@code inbound.inspection.completed} (1:1 per ASN)</li>
 *   <li>{@code admin_throughput_inbound_daily} — incremented from
 *       {@code inbound.putaway.completed} (atomic counter, idempotency.md § 2.6)</li>
 * </ul>
 */
@Service
public class InboundProjectionService {

    private static final String SOURCE_SERVICE = "inbound";

    private final AsnSummaryRepository asnRepo;
    private final InspectionSummaryRepository inspectionRepo;
    private final ThroughputInboundDailyRepository throughputRepo;
    private final PartnerRefRepository partnerRepo;
    private final AdminEventDedupePort dedupe;
    private final ProjectionMetrics metrics;
    private final Clock clock;

    public InboundProjectionService(AsnSummaryRepository asnRepo,
                                    InspectionSummaryRepository inspectionRepo,
                                    ThroughputInboundDailyRepository throughputRepo,
                                    PartnerRefRepository partnerRepo,
                                    AdminEventDedupePort dedupe,
                                    ProjectionMetrics metrics,
                                    Clock clock) {
        this.asnRepo = asnRepo;
        this.inspectionRepo = inspectionRepo;
        this.throughputRepo = throughputRepo;
        this.partnerRepo = partnerRepo;
        this.dedupe = dedupe;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public DedupeOutcome project(ProjectionEnvelope envelope) {
        DedupeOutcome outcome = dedupe.tryRecord(envelope.eventId(), envelope.eventType());
        if (outcome == DedupeOutcome.DUPLICATE) {
            metrics.recordDropped("duplicate");
            return outcome;
        }

        DedupeOutcome applied = dispatch(envelope);
        if (applied == DedupeOutcome.IGNORED_DUPLICATE_LATE) {
            dedupe.markStale(envelope.eventId());
            metrics.recordDropped("stale");
        } else {
            metrics.recordLag(SOURCE_SERVICE, envelope.sourceTopic(), envelope.occurredAt());
        }
        return applied;
    }

    private DedupeOutcome dispatch(ProjectionEnvelope envelope) {
        switch (envelope.eventType()) {
            case "inbound.asn.received":
                return onAsnReceived(envelope);
            case "inbound.asn.cancelled":
                return onAsnStatus(envelope, "CANCELLED");
            case "inbound.asn.closed":
                return onAsnClosed(envelope);
            case "inbound.inspection.completed":
                return onInspectionCompleted(envelope);
            case "inbound.putaway.completed":
                return onPutawayCompleted(envelope);
            case "inbound.putaway.instructed":
                // architecture lists this as ops-only; no read-model effect.
                return DedupeOutcome.APPLIED;
            default:
                throw new UnknownEventTypeException(envelope.eventType(), envelope.sourceTopic());
        }
    }

    private DedupeOutcome onAsnReceived(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID asnId = uuid(p, "asnId");
        Instant occurredAt = envelope.occurredAt();
        AsnSummaryEntity row = asnRepo.findById(asnId).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        UUID supplierId = optionalUuid(p, "supplierPartnerId");
        String supplierName = resolvePartnerName(supplierId);
        int lineCount = p.has("lines") && p.get("lines").isArray() ? p.get("lines").size() : 0;
        if (row == null) {
            row = new AsnSummaryEntity(
                    asnId,
                    text(p, "asnNo"),
                    uuid(p, "warehouseId"),
                    supplierId,
                    supplierName,
                    "CREATED",
                    optionalText(p, "source"),
                    optionalDate(p, "expectedArriveDate"),
                    lineCount,
                    occurredAt,
                    null,
                    occurredAt);
            asnRepo.save(row);
        } else {
            row.applyReceived(text(p, "asnNo"), uuid(p, "warehouseId"), supplierId,
                    supplierName, optionalText(p, "source"),
                    optionalDate(p, "expectedArriveDate"), lineCount, occurredAt, occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onAsnStatus(ProjectionEnvelope envelope, String newStatus) {
        JsonNode p = envelope.payload();
        UUID asnId = uuid(p, "asnId");
        Instant occurredAt = envelope.occurredAt();
        AsnSummaryEntity row = asnRepo.findById(asnId).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            // Out-of-order: status before received. Insert a thin row with
            // best-effort fields. denorm columns may be null until the
            // received event catches up (architecture.md § Out-of-Order).
            row = new AsnSummaryEntity(
                    asnId,
                    optionalText(p, "asnNo") == null ? "" : optionalText(p, "asnNo"),
                    optionalUuid(p, "warehouseId") == null
                            ? new UUID(0, 0)
                            : optionalUuid(p, "warehouseId"),
                    null, null, newStatus, null, null, 0, null, null, occurredAt);
            asnRepo.save(row);
        } else {
            row.applyStatus(newStatus, occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onAsnClosed(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID asnId = uuid(p, "asnId");
        Instant occurredAt = envelope.occurredAt();
        AsnSummaryEntity row = asnRepo.findById(asnId).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        Instant closedAt = optionalInstant(p, "closedAt");
        if (row == null) {
            row = new AsnSummaryEntity(
                    asnId,
                    optionalText(p, "asnNo") == null ? "" : optionalText(p, "asnNo"),
                    optionalUuid(p, "warehouseId") == null
                            ? new UUID(0, 0)
                            : optionalUuid(p, "warehouseId"),
                    null, null, "CLOSED", null, null, 0, null, closedAt, occurredAt);
            asnRepo.save(row);
        } else {
            row.applyClosed(closedAt == null ? occurredAt : closedAt, occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onInspectionCompleted(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID asnId = uuid(p, "asnId");
        Instant occurredAt = envelope.occurredAt();
        InspectionSummaryEntity row = inspectionRepo.findById(asnId).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        UUID warehouseId = uuid(p, "warehouseId");
        String inspectorId = optionalText(p, "inspectorId");
        Instant completedAt = optionalInstant(p, "completedAt");
        Instant inspectionAt = completedAt == null ? occurredAt : completedAt;
        int discrepancyCount = optionalInteger(p, "discrepancyCount", 0);
        int totalLines = 0;
        int totalQtyExpected = 0;
        int totalQtyPassed = 0;
        int totalQtyDamaged = 0;
        int totalQtyShort = 0;
        if (p.has("lines") && p.get("lines").isArray()) {
            for (JsonNode line : p.get("lines")) {
                totalLines++;
                totalQtyExpected += optionalInteger(line, "expectedQty", 0);
                totalQtyPassed += optionalInteger(line, "qtyPassed", 0);
                totalQtyDamaged += optionalInteger(line, "qtyDamaged", 0);
                totalQtyShort += optionalInteger(line, "qtyShort", 0);
            }
        }
        if (row == null) {
            row = new InspectionSummaryEntity(asnId, warehouseId, inspectionAt, inspectorId,
                    totalLines, discrepancyCount, totalQtyExpected, totalQtyPassed,
                    totalQtyDamaged, totalQtyShort, occurredAt);
            inspectionRepo.save(row);
        } else {
            row.apply(warehouseId, inspectionAt, inspectorId, totalLines, discrepancyCount,
                    totalQtyExpected, totalQtyPassed, totalQtyDamaged, totalQtyShort,
                    occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onPutawayCompleted(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        UUID warehouseId = uuid(p, "warehouseId");
        Instant occurredAt = envelope.occurredAt();
        Instant completedAt = optionalInstant(p, "completedAt");
        Instant effectiveAt = completedAt == null ? occurredAt : completedAt;
        LocalDate date = effectiveAt.atZone(ZoneOffset.UTC).toLocalDate();
        int qtyDelta = 0;
        JsonNode lines = requireArray(p, "lines");
        for (JsonNode line : lines) {
            qtyDelta += optionalInteger(line, "qtyReceived", 0);
        }

        // Atomic LWW upsert: insert or +1 with WHERE last_event_at <
        // EXCLUDED.last_event_at. 0 affected rows = stale event silently
        // skipped (no read-modify-write race window cross-pod).
        int affected = throughputRepo.upsertIncrement(date, warehouseId, qtyDelta, occurredAt);
        if (affected == 0) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        return DedupeOutcome.APPLIED;
    }

    private String resolvePartnerName(UUID partnerId) {
        if (partnerId == null) return null;
        return partnerRepo.findById(partnerId).map(PartnerRefEntity::getName).orElse(null);
    }
}
