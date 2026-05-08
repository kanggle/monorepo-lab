package com.wms.admin.application.projection;

import static com.wms.admin.application.projection.PayloadJson.optionalDate;
import static com.wms.admin.application.projection.PayloadJson.optionalText;
import static com.wms.admin.application.projection.PayloadJson.optionalUuid;
import static com.wms.admin.application.projection.PayloadJson.requireObject;
import static com.wms.admin.application.projection.PayloadJson.text;
import static com.wms.admin.application.projection.PayloadJson.uuid;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.admin.application.port.AdminEventDedupePort;
import com.wms.admin.infra.observability.ProjectionMetrics;
import com.wms.admin.readmodel.master.LocationRefEntity;
import com.wms.admin.readmodel.master.LocationRefRepository;
import com.wms.admin.readmodel.master.LotRefEntity;
import com.wms.admin.readmodel.master.LotRefRepository;
import com.wms.admin.readmodel.master.PartnerRefEntity;
import com.wms.admin.readmodel.master.PartnerRefRepository;
import com.wms.admin.readmodel.master.SkuRefEntity;
import com.wms.admin.readmodel.master.SkuRefRepository;
import com.wms.admin.readmodel.master.WarehouseRefEntity;
import com.wms.admin.readmodel.master.WarehouseRefRepository;
import com.wms.admin.readmodel.master.ZoneRefEntity;
import com.wms.admin.readmodel.master.ZoneRefRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects {@code wms.master.{warehouse|zone|location|sku|partner|lot}.v1} into
 * the corresponding {@code admin_*_ref} read-model tables.
 *
 * <p>Per {@code architecture.md § Read-Model Projection Pattern}:
 *
 * <ol>
 *   <li>Run inside a single {@code @Transactional} boundary.</li>
 *   <li>Insert into {@code admin_event_dedupe} ({@code eventId} as PK).
 *       Duplicate insert → skip (Kafka redelivery).</li>
 *   <li>If the existing read-model row's {@code last_event_at} is newer than
 *       the event's {@code occurredAt}, update the dedupe row outcome to
 *       {@code IGNORED_DUPLICATE_LATE} and skip the mutation (LWW guard).</li>
 *   <li>Otherwise apply the upsert.</li>
 * </ol>
 *
 * <p>Each event type expects a {@code payload.{aggregate}} object with the
 * full snapshot (master-events.md § "updated — Snapshot or Diff?"). Missing
 * mandatory fields throw {@link IllegalArgumentException} → DLT.
 */
@Service
public class MasterProjectionService {

    private static final Logger log = LoggerFactory.getLogger(MasterProjectionService.class);
    private static final String SOURCE_SERVICE = "master";

    private final WarehouseRefRepository warehouseRepo;
    private final ZoneRefRepository zoneRepo;
    private final LocationRefRepository locationRepo;
    private final SkuRefRepository skuRepo;
    private final LotRefRepository lotRepo;
    private final PartnerRefRepository partnerRepo;
    private final AdminEventDedupePort dedupe;
    private final ProjectionMetrics metrics;

    public MasterProjectionService(WarehouseRefRepository warehouseRepo,
                                   ZoneRefRepository zoneRepo,
                                   LocationRefRepository locationRepo,
                                   SkuRefRepository skuRepo,
                                   LotRefRepository lotRepo,
                                   PartnerRefRepository partnerRepo,
                                   AdminEventDedupePort dedupe,
                                   ProjectionMetrics metrics) {
        this.warehouseRepo = warehouseRepo;
        this.zoneRepo = zoneRepo;
        this.locationRepo = locationRepo;
        this.skuRepo = skuRepo;
        this.lotRepo = lotRepo;
        this.partnerRepo = partnerRepo;
        this.dedupe = dedupe;
        this.metrics = metrics;
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
        String type = envelope.eventType();
        if (type.startsWith("master.warehouse.")) {
            return onWarehouse(envelope);
        }
        if (type.startsWith("master.zone.")) {
            return onZone(envelope);
        }
        if (type.startsWith("master.location.")) {
            return onLocation(envelope);
        }
        if (type.startsWith("master.sku.")) {
            return onSku(envelope);
        }
        if (type.startsWith("master.partner.")) {
            return onPartner(envelope);
        }
        if (type.startsWith("master.lot.")) {
            return onLot(envelope);
        }
        throw new UnknownEventTypeException(type, envelope.sourceTopic());
    }

    private DedupeOutcome onWarehouse(ProjectionEnvelope envelope) {
        JsonNode w = requireObject(envelope.payload(), "warehouse");
        UUID id = uuid(w, "id");
        Instant occurredAt = envelope.occurredAt();
        WarehouseRefEntity row = warehouseRepo.findById(id).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            row = new WarehouseRefEntity(
                    id,
                    text(w, "warehouseCode"),
                    text(w, "name"),
                    optionalText(w, "timezone"),
                    text(w, "status"),
                    occurredAt);
            warehouseRepo.save(row);
        } else {
            row.apply(text(w, "warehouseCode"), text(w, "name"),
                    optionalText(w, "timezone"), text(w, "status"), occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onZone(ProjectionEnvelope envelope) {
        JsonNode z = requireObject(envelope.payload(), "zone");
        UUID id = uuid(z, "id");
        Instant occurredAt = envelope.occurredAt();
        ZoneRefEntity row = zoneRepo.findById(id).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            row = new ZoneRefEntity(
                    id,
                    uuid(z, "warehouseId"),
                    text(z, "zoneCode"),
                    text(z, "name"),
                    optionalText(z, "zoneType"),
                    text(z, "status"),
                    occurredAt);
            zoneRepo.save(row);
        } else {
            row.apply(uuid(z, "warehouseId"), text(z, "zoneCode"), text(z, "name"),
                    optionalText(z, "zoneType"), text(z, "status"), occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onLocation(ProjectionEnvelope envelope) {
        JsonNode l = requireObject(envelope.payload(), "location");
        UUID id = uuid(l, "id");
        Instant occurredAt = envelope.occurredAt();
        LocationRefEntity row = locationRepo.findById(id).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            row = new LocationRefEntity(
                    id,
                    text(l, "locationCode"),
                    uuid(l, "warehouseId"),
                    optionalUuid(l, "zoneId"),
                    optionalText(l, "locationType"),
                    text(l, "status"),
                    occurredAt);
            locationRepo.save(row);
        } else {
            row.apply(text(l, "locationCode"), uuid(l, "warehouseId"),
                    optionalUuid(l, "zoneId"), optionalText(l, "locationType"),
                    text(l, "status"), occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onSku(ProjectionEnvelope envelope) {
        JsonNode s = requireObject(envelope.payload(), "sku");
        UUID id = uuid(s, "id");
        Instant occurredAt = envelope.occurredAt();
        SkuRefEntity row = skuRepo.findById(id).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            row = new SkuRefEntity(
                    id,
                    text(s, "skuCode"),
                    text(s, "name"),
                    optionalText(s, "baseUom"),
                    optionalText(s, "trackingType"),
                    text(s, "status"),
                    occurredAt);
            skuRepo.save(row);
        } else {
            row.apply(text(s, "skuCode"), text(s, "name"), optionalText(s, "baseUom"),
                    optionalText(s, "trackingType"), text(s, "status"), occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onPartner(ProjectionEnvelope envelope) {
        JsonNode p = requireObject(envelope.payload(), "partner");
        UUID id = uuid(p, "id");
        Instant occurredAt = envelope.occurredAt();
        PartnerRefEntity row = partnerRepo.findById(id).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        if (row == null) {
            row = new PartnerRefEntity(
                    id,
                    text(p, "partnerCode"),
                    text(p, "name"),
                    optionalText(p, "partnerType"),
                    text(p, "status"),
                    occurredAt);
            partnerRepo.save(row);
        } else {
            row.apply(text(p, "partnerCode"), text(p, "name"),
                    optionalText(p, "partnerType"), text(p, "status"), occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }

    private DedupeOutcome onLot(ProjectionEnvelope envelope) {
        JsonNode l = requireObject(envelope.payload(), "lot");
        UUID id = uuid(l, "id");
        Instant occurredAt = envelope.occurredAt();
        LotRefEntity row = lotRepo.findById(id).orElse(null);
        if (row != null && row.getLastEventAt().isAfter(occurredAt)) {
            return DedupeOutcome.IGNORED_DUPLICATE_LATE;
        }
        LocalDate expiry = optionalDate(l, "expiryDate");
        if (row == null) {
            row = new LotRefEntity(
                    id,
                    uuid(l, "skuId"),
                    text(l, "lotNo"),
                    expiry,
                    text(l, "status"),
                    occurredAt);
            lotRepo.save(row);
        } else {
            row.apply(uuid(l, "skuId"), text(l, "lotNo"), expiry,
                    text(l, "status"), occurredAt);
        }
        return DedupeOutcome.APPLIED;
    }
}
