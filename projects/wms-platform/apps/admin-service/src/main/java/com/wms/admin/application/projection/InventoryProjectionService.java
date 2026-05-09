package com.wms.admin.application.projection;

import static com.wms.admin.application.projection.PayloadJson.optionalIntegerBoxed;
import static com.wms.admin.application.projection.PayloadJson.optionalText;
import static com.wms.admin.application.projection.PayloadJson.optionalUuid;
import static com.wms.admin.application.projection.PayloadJson.text;
import static com.wms.admin.application.projection.PayloadJson.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.admin.application.repository.AdminEventDedupeRepository;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import com.wms.admin.readmodel.inventory.AdjustmentAuditEntity;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotId;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import com.wms.admin.readmodel.master.LocationRefEntity;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefEntity;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.SkuRefEntity;
import com.wms.admin.readmodel.master.SkuRefRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Projects {@code wms.inventory.*} into:
 *
 * <ul>
 *   <li>{@code admin_inventory_snapshot} — primary dashboard row, upsert by
 *       {@code (locationId, skuId, lotId)}; LWW guarded.</li>
 *   <li>{@code admin_adjustment_audit} — append-only by {@code id = eventId}
 *       (PK collision = additional dedupe safety net).</li>
 *   <li>{@code admin_alert_log} — append-only by {@code id = eventId}.</li>
 * </ul>
 *
 * <p>{@code inventory.transferred} writes both source and target snapshot rows
 * (cross-aggregate dual-write); the source/target lock order follows the
 * id-ascending convention from the sibling {@code TransferStockService} to
 * avoid deadlocks (failure-scenarios: PostgreSQL upsert deadlock).
 */
@Service
public class InventoryProjectionService extends AbstractProjectionService {

    private static final String SOURCE_SERVICE = "inventory";

    private final InventorySnapshotRepository snapshotRepo;
    private final AdjustmentAuditRepository auditRepo;
    private final AlertLogRepository alertRepo;
    private final LocationRefRepository locationRepo;
    private final SkuRefRepository skuRepo;
    private final LotRefRepository lotRepo;
    private final Clock clock;

    public InventoryProjectionService(InventorySnapshotRepository snapshotRepo,
                                      AdjustmentAuditRepository auditRepo,
                                      AlertLogRepository alertRepo,
                                      LocationRefRepository locationRepo,
                                      SkuRefRepository skuRepo,
                                      LotRefRepository lotRepo,
                                      AdminEventDedupeRepository dedupe,
                                      ProjectionMetrics metrics,
                                      Clock clock) {
        super(dedupe, metrics);
        this.snapshotRepo = snapshotRepo;
        this.auditRepo = auditRepo;
        this.alertRepo = alertRepo;
        this.locationRepo = locationRepo;
        this.skuRepo = skuRepo;
        this.lotRepo = lotRepo;
        this.clock = clock;
    }

    @Override
    protected String sourceService() {
        return SOURCE_SERVICE;
    }

    @Override
    protected DedupeOutcome dispatch(ProjectionEnvelope envelope) {
        switch (envelope.eventType()) {
            case "inventory.received":
                return onLineLevelEvent(envelope, true);
            case "inventory.adjusted":
                return onAdjusted(envelope);
            case "inventory.transferred":
                return onTransferred(envelope);
            case "inventory.reserved":
            case "inventory.released":
                return onLineLevelEvent(envelope, false);
            case "inventory.confirmed":
                return onLineLevelEvent(envelope, false);
            case "inventory.low-stock-detected":
                return onLowStockDetected(envelope);
            default:
                throw new UnknownEventTypeException(envelope.eventType(), envelope.sourceTopic());
        }
    }

    /**
     * Common handler for {@code received | reserved | released | confirmed}.
     * Each carries a {@code lines[]} array with per-row absolute quantities
     * after the mutation. Upsert each line; LWW guarded.
     */
    private DedupeOutcome onLineLevelEvent(ProjectionEnvelope envelope, boolean inventoryReceived) {
        JsonNode p = envelope.payload();
        UUID warehouseId = optionalUuid(p, "warehouseId");
        if (!p.has("lines") || !p.get("lines").isArray()) {
            // Confirmation event with no lines is unusual but tolerable.
            return DedupeOutcome.APPLIED;
        }
        Instant occurredAt = envelope.occurredAt();
        boolean anyApplied = false;
        boolean anyStale = false;
        for (JsonNode line : p.get("lines")) {
            UUID locationId = uuid(line, "locationId");
            UUID skuId = uuid(line, "skuId");
            UUID lotId = optionalUuid(line, "lotId");
            DedupeOutcome r = upsertSnapshot(envelope, warehouseId, locationId, skuId, lotId,
                    line, occurredAt);
            if (r == DedupeOutcome.IGNORED_DUPLICATE_LATE) anyStale = true;
            else anyApplied = true;
        }
        if (!anyApplied && anyStale) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onAdjusted(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        Instant occurredAt = envelope.occurredAt();

        appendAuditRow(envelope, p);

        // Snapshot row update (current state in payload.inventory).
        UUID locationId = uuid(p, "locationId");
        UUID skuId = uuid(p, "skuId");
        UUID lotId = optionalUuid(p, "lotId");
        if (p.has("inventory") && p.get("inventory").isObject()) {
            JsonNode inv = p.get("inventory");
            int avail = optionalIntegerBoxed(inv, "availableQty") == null
                    ? 0 : inv.get("availableQty").asInt();
            int reserved = optionalIntegerBoxed(inv, "reservedQty") == null
                    ? 0 : inv.get("reservedQty").asInt();
            int damaged = optionalIntegerBoxed(inv, "damagedQty") == null
                    ? 0 : inv.get("damagedQty").asInt();
            UUID warehouseId = optionalUuid(p, "warehouseId");
            return upsertSnapshotAbsolute(locationId, skuId, lotId, warehouseId, avail,
                    reserved, damaged, occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    /**
     * Append-only audit row keyed by {@code eventId} (PK). Skipped if a row
     * already exists (Kafka redelivery safety net beyond the
     * {@code admin_event_dedupe} pre-check).
     */
    private void appendAuditRow(ProjectionEnvelope envelope, JsonNode p) {
        UUID auditId = envelope.eventId();
        if (auditRepo.existsById(auditId)) {
            return;
        }
        UUID locationId = uuid(p, "locationId");
        UUID skuId = uuid(p, "skuId");
        UUID lotId = optionalUuid(p, "lotId");
        UUID warehouseId = optionalUuid(p, "warehouseId");
        if (warehouseId == null) {
            warehouseId = locationRepo.findById(locationId)
                    .map(LocationRefEntity::getWarehouseId)
                    .orElse(new UUID(0, 0));
        }
        Integer delta = optionalIntegerBoxed(p, "delta");
        AdjustmentAuditEntity audit = new AdjustmentAuditEntity(
                auditId,
                locationId,
                skuId,
                lotId,
                warehouseId,
                optionalText(p, "bucket") == null ? "AVAILABLE" : optionalText(p, "bucket"),
                delta == null ? 0 : delta,
                optionalText(p, "reasonCode"),
                optionalText(p, "reasonNote"),
                optionalText(p, "actorId"),
                envelope.occurredAt(),
                clock.instant());
        auditRepo.save(audit);
    }

    private DedupeOutcome onTransferred(ProjectionEnvelope envelope) {
        JsonNode p = envelope.payload();
        Instant occurredAt = envelope.occurredAt();
        UUID warehouseId = optionalUuid(p, "warehouseId");
        UUID skuId = uuid(p, "skuId");
        UUID lotId = optionalUuid(p, "lotId");

        JsonNode source = p.has("source") ? p.get("source") : null;
        JsonNode target = p.has("target") ? p.get("target") : null;
        if (source == null || target == null) {
            throw new IllegalArgumentException("inventory.transferred missing source/target");
        }
        UUID sourceLocation = uuid(source, "locationId");
        UUID targetLocation = uuid(target, "locationId");
        Integer sourceAfter = optionalIntegerBoxed(source, "availableQtyAfter");
        Integer targetAfter = optionalIntegerBoxed(target, "availableQtyAfter");

        // id-ascending lock order to avoid deadlock with concurrent transfers
        // (sibling TransferStockService pattern, idempotency.md § Failure
        // Scenarios "PostgreSQL upsert deadlock").
        boolean sourceFirst = sourceLocation.compareTo(targetLocation) < 0;
        UUID firstLoc = sourceFirst ? sourceLocation : targetLocation;
        UUID secondLoc = sourceFirst ? targetLocation : sourceLocation;
        Integer firstAfter = sourceFirst ? sourceAfter : targetAfter;
        Integer secondAfter = sourceFirst ? targetAfter : sourceAfter;

        DedupeOutcome a = upsertAvailableOnly(firstLoc, skuId, lotId, warehouseId, firstAfter,
                occurredAt);
        DedupeOutcome b = upsertAvailableOnly(secondLoc, skuId, lotId, warehouseId, secondAfter,
                occurredAt);
        if (a == DedupeOutcome.IGNORED_DUPLICATE_LATE
                && b == DedupeOutcome.IGNORED_DUPLICATE_LATE) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onLowStockDetected(ProjectionEnvelope envelope) {
        // Append-only by id = eventId. Duplicate insert silently fails via PK.
        UUID id = envelope.eventId();
        if (alertRepo.existsById(id)) {
            return DedupeOutcome.APPLIED;
        }
        JsonNode p = envelope.payload();
        AlertLogEntity row = new AlertLogEntity(
                id,
                "LOW_STOCK",
                optionalUuid(p, "warehouseId"),
                optionalUuid(p, "locationId"),
                optionalUuid(p, "skuId"),
                optionalUuid(p, "lotId"),
                optionalIntegerBoxed(p, "threshold"),
                optionalIntegerBoxed(p, "availableQty"),
                envelope.occurredAt(),
                clock.instant());
        alertRepo.save(row);
        return DedupeOutcome.APPLIED;
    }

    // ----- snapshot mutation helpers -------------------------------------

    /**
     * Result of {@link #findExistingAndCheckLww}:
     *
     * <ul>
     *   <li>{@code stale = true} — existing row's {@code lastEventAt} is newer
     *       than the incoming event; the caller must short-circuit with
     *       {@link DedupeOutcome#IGNORED_DUPLICATE_LATE}.</li>
     *   <li>{@code stale = false}, {@code existing != null} — existing row is
     *       older or equal; safe to upsert.</li>
     *   <li>{@code stale = false}, {@code existing == null} — no row yet; safe
     *       to insert.</li>
     * </ul>
     *
     * <p>{@code effectiveWarehouseId} encodes the warehouseId resolution rule
     * shared by all three upsert variants: prefer the event-supplied value,
     * fall back to the existing row's value, finally fall back to the
     * all-zero UUID sentinel.
     */
    private record LwwLookup(InventorySnapshotEntity existing,
                             boolean stale,
                             UUID effectiveWarehouseId) {
    }

    private LwwLookup findExistingAndCheckLww(UUID locationId, UUID skuId, UUID lotId,
                                              UUID warehouseId, Instant occurredAt) {
        InventorySnapshotEntity existing = snapshotRepo.findById(
                new InventorySnapshotId(locationId, skuId, lotId)).orElse(null);
        boolean stale = existing != null && existing.getLastEventAt().isAfter(occurredAt);
        UUID effectiveWh = warehouseId != null ? warehouseId
                : (existing != null ? existing.getWarehouseId() : new UUID(0, 0));
        return new LwwLookup(existing, stale, effectiveWh);
    }

    private DedupeOutcome upsertSnapshot(ProjectionEnvelope envelope, UUID warehouseId,
                                         UUID locationId, UUID skuId, UUID lotId,
                                         JsonNode line, Instant occurredAt) {
        // damagedQty is rarely emitted on these events — keep current row's
        // value if absent. We use a single-call upsertSnapshotAbsolute
        // signature, so for received/reserved/released/confirmed we read the
        // current row's damagedQty when computing the new state.
        LwwLookup lookup = findExistingAndCheckLww(locationId, skuId, lotId, warehouseId,
                occurredAt);
        if (lookup.stale()) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        InventorySnapshotEntity existing = lookup.existing();
        Integer availableAfter = optionalIntegerBoxed(line, "availableQtyAfter");
        Integer reservedAfter = optionalIntegerBoxed(line, "reservedQtyAfter");
        int avail = availableAfter != null ? availableAfter
                : (existing != null ? existing.getAvailableQty() : 0);
        int reserved = reservedAfter != null ? reservedAfter
                : (existing != null ? existing.getReservedQty() : 0);
        int damaged = existing != null ? existing.getDamagedQty() : 0;
        applySnapshot(existing, locationId, skuId, lotId, lookup.effectiveWarehouseId(), avail,
                reserved, damaged, occurredAt);
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome upsertSnapshotAbsolute(UUID locationId, UUID skuId, UUID lotId,
                                                 UUID warehouseId, int availableQty,
                                                 int reservedQty, int damagedQty,
                                                 Instant occurredAt) {
        LwwLookup lookup = findExistingAndCheckLww(locationId, skuId, lotId, warehouseId,
                occurredAt);
        if (lookup.stale()) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        applySnapshot(lookup.existing(), locationId, skuId, lotId, lookup.effectiveWarehouseId(),
                availableQty, reservedQty, damagedQty, occurredAt);
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome upsertAvailableOnly(UUID locationId, UUID skuId, UUID lotId,
                                              UUID warehouseId, Integer availableAfter,
                                              Instant occurredAt) {
        LwwLookup lookup = findExistingAndCheckLww(locationId, skuId, lotId, warehouseId,
                occurredAt);
        if (lookup.stale()) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        InventorySnapshotEntity existing = lookup.existing();
        int avail = availableAfter != null ? availableAfter
                : (existing != null ? existing.getAvailableQty() : 0);
        int reserved = existing != null ? existing.getReservedQty() : 0;
        int damaged = existing != null ? existing.getDamagedQty() : 0;
        applySnapshot(existing, locationId, skuId, lotId, lookup.effectiveWarehouseId(), avail,
                reserved, damaged, occurredAt);
        return DedupeOutcome.APPLIED;
    }

    private void applySnapshot(InventorySnapshotEntity existing, UUID locationId, UUID skuId,
                               UUID lotId, UUID warehouseId, int availableQty, int reservedQty,
                               int damagedQty, Instant occurredAt) {
        boolean lowStock = availableQty <= 10; // v1 default threshold; recomputed at projection.
        String locationCode = locationRepo.findById(locationId)
                .map(LocationRefEntity::getLocationCode).orElse(null);
        String skuCode = skuRepo.findById(skuId).map(SkuRefEntity::getSkuCode).orElse(null);
        String lotNo = lotId == null ? null
                : lotRepo.findById(lotId).map(LotRefEntity::getLotNo).orElse(null);
        if (existing == null) {
            InventorySnapshotEntity row = new InventorySnapshotEntity(
                    locationId, skuId, lotId, warehouseId, locationCode, skuCode, lotNo,
                    availableQty, reservedQty, damagedQty, lowStock, occurredAt, occurredAt);
            snapshotRepo.save(row);
        } else {
            existing.apply(warehouseId, locationCode, skuCode, lotNo, availableQty, reservedQty,
                    damagedQty, lowStock, occurredAt, occurredAt);
        }
    }
}
