package com.example.scmplatform.inventoryvisibility.application.service;

import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.repository.InventorySnapshotRepository;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessThreshold;
import com.example.scmplatform.inventoryvisibility.domain.staleness.repository.NodeStalenessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Central application service for inventory-visibility-service.
 *
 * <p>Handles three Kafka event types from wms-platform (cross-project),
 * plus the staleness detection batch and all read queries.
 *
 * <p>Layer contracts:
 * <ul>
 *   <li>No framework classes in domain objects — only in this service and adapters.</li>
 *   <li>Transactional boundary: one DB transaction per event or query.</li>
 *   <li>Idempotency: duplicate eventId check via EventDedupePort before any mutation.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryVisibilityApplicationService {

    private final InventoryNodeRepository nodeRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final NodeStalenessRepository stalenessRepository;
    private final EventDedupePort eventDedupePort;
    private final AlertPublisherPort alertPublisherPort;
    private final ClockPort clock;

    @Value("${inventory-visibility.staleness.threshold-seconds:600}")
    private long thresholdSeconds;

    // -------------------------------------------------------------------------
    // Event consumer use cases (called by Kafka consumers)
    // -------------------------------------------------------------------------

    /**
     * Process wms.inventory.received.v1 — upsert snapshot with received quantity.
     * Edge Case 3: auto-register node if not found.
     */
    @Transactional
    public void applyInventoryReceived(String warehouseId, String skuId,
                                        long qtyReceived, String warehouseCode,
                                        UUID eventId,
                                        Instant occurredAt, String tenantId,
                                        String sourceTopic) {
        if (eventDedupePort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }
        InventoryNode node = resolveOrCreateNode(warehouseId, NodeType.WMS_WAREHOUSE, tenantId, warehouseCode);
        applySnapshotDelta(node.getId(), Sku.of(skuId),
                Quantity.of(BigDecimal.valueOf(qtyReceived)), true,
                eventId, occurredAt, tenantId);
        updateStaleness(node.getId(), tenantId, eventId, occurredAt);
        eventDedupePort.markProcessed(eventId, tenantId, clock.now(), sourceTopic);
        log.info("applied inventory.received: node={} sku={} qty={} eventId={}",
                node.getId(), skuId, qtyReceived, eventId);
    }

    /**
     * Process wms.inventory.adjusted.v1 — apply delta (positive or negative).
     */
    @Transactional
    public void applyInventoryAdjusted(String warehouseId, String skuId,
                                        long delta, String warehouseCode, UUID eventId,
                                        Instant occurredAt, String tenantId,
                                        String sourceTopic) {
        if (eventDedupePort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }
        InventoryNode node = resolveOrCreateNode(warehouseId, NodeType.WMS_WAREHOUSE, tenantId, warehouseCode);
        Quantity absDelta = Quantity.of(BigDecimal.valueOf(Math.abs(delta)));
        boolean isAddition = delta >= 0;

        // When no snapshot exists yet and the adjustment is a subtraction, start at ZERO
        // (no negative inventory: applySnapshotDelta will create with zero base for subtraction)
        applySnapshotDelta(node.getId(), Sku.of(skuId), absDelta, isAddition,
                eventId, occurredAt, tenantId);
        updateStaleness(node.getId(), tenantId, eventId, occurredAt);
        eventDedupePort.markProcessed(eventId, tenantId, clock.now(), sourceTopic);
        log.info("applied inventory.adjusted: node={} sku={} delta={} eventId={}",
                node.getId(), skuId, delta, eventId);
    }

    /**
     * Process wms.inventory.transferred.v1 — atomic: decrement source, increment destination.
     * Acceptance Criteria #10: source/destination update in single transaction.
     */
    @Transactional
    public void applyInventoryTransferred(String sourceWarehouseId, String destWarehouseId,
                                           String skuId, long quantity,
                                           String warehouseCode,
                                           UUID eventId, Instant occurredAt,
                                           String tenantId, String sourceTopic) {
        if (eventDedupePort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }
        // Transfers are intra-warehouse, so both endpoints carry the same warehouse code.
        InventoryNode srcNode = resolveOrCreateNode(sourceWarehouseId, NodeType.WMS_WAREHOUSE, tenantId, warehouseCode);
        InventoryNode dstNode = resolveOrCreateNode(destWarehouseId, NodeType.WMS_WAREHOUSE, tenantId, warehouseCode);
        Quantity qty = Quantity.of(BigDecimal.valueOf(quantity));
        Sku sku = Sku.of(skuId);

        // Decrement source, increment destination — both in the same @Transactional scope
        applySnapshotDelta(srcNode.getId(), sku, qty, false, eventId, occurredAt, tenantId);
        applySnapshotDelta(dstNode.getId(), sku, qty, true, eventId, occurredAt, tenantId);

        updateStaleness(srcNode.getId(), tenantId, eventId, occurredAt);
        updateStaleness(dstNode.getId(), tenantId, eventId, occurredAt);
        eventDedupePort.markProcessed(eventId, tenantId, clock.now(), sourceTopic);
        log.info("applied inventory.transferred: src={} dst={} sku={} qty={} eventId={}",
                srcNode.getId(), dstNode.getId(), skuId, quantity, eventId);
    }

    // -------------------------------------------------------------------------
    // Query use cases (called by REST controllers)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InventorySnapshot> getCrossNodeSnapshot(String tenantId, int page, int size) {
        return snapshotRepository.findAll(tenantId, page, size);
    }

    @Transactional(readOnly = true)
    public long countCrossNodeSnapshot(String tenantId) {
        return snapshotRepository.countAll(tenantId);
    }

    /**
     * Cross-tenant snapshot for the demand-planning replenishment batch
     * (ADR-MONO-027 §D7.1). Served only by the internal network-trusted endpoint
     * (no JWT, no tenant claim — the batch is tenant-agnostic).
     */
    @Transactional(readOnly = true)
    public List<InventorySnapshot> getAllSnapshotsAcrossTenants() {
        return snapshotRepository.findAllAcrossTenants();
    }

    /**
     * As {@link #getAllSnapshotsAcrossTenants()}, but each snapshot is paired with its
     * node's business {@code warehouseCode} (ADR-MONO-050 D9 / TASK-SCM-BE-037) so the
     * demand-planning batch sweep can address a replenishment PO by code rather than uuid.
     *
     * <p>The code is nullable: the owning node may not have learned one yet (wms emits it
     * best-effort). A null code never omits the row — it only means the downstream
     * inbound-expected addressing is skipped for that warehouse.
     */
    @Transactional(readOnly = true)
    public List<SnapshotWithWarehouseCode> getAllSnapshotsAcrossTenantsWithWarehouseCode() {
        // Snapshots are keyed per (node, sku), so ONE warehouse node backs many rows and a
        // naive per-row lookup re-reads the same node repeatedly. Memoise for the duration of
        // this read: the projection then costs one lookup per DISTINCT node instead of one per
        // snapshot row (the sweep reads every row). Deliberately local — no batch-fetch method
        // is added to the node port for a read concern, and a call-scoped map cannot go stale.
        // NOTE: containsKey/put, not computeIfAbsent — computeIfAbsent does NOT record a null
        // result, so it would re-query on every row for exactly the nodes whose code is still
        // null (the initial / fail-closed state). Caching the null is the whole point here.
        Map<NodeId, String> warehouseCodeByNode = new HashMap<>();
        return snapshotRepository.findAllAcrossTenants().stream()
                .map(s -> {
                    NodeId nodeId = s.getNodeId();
                    if (!warehouseCodeByNode.containsKey(nodeId)) {
                        warehouseCodeByNode.put(nodeId, nodeRepository.findById(nodeId)
                                .map(InventoryNode::getWarehouseCode)
                                .orElse(null));
                    }
                    return new SnapshotWithWarehouseCode(s, warehouseCodeByNode.get(nodeId));
                })
                .toList();
    }

    /** Read-projection pairing a snapshot with its node's nullable warehouse code. */
    public record SnapshotWithWarehouseCode(InventorySnapshot snapshot, String warehouseCode) {
    }

    @Transactional(readOnly = true)
    public List<InventorySnapshot> getSnapshotByNode(String nodeId, String tenantId) {
        NodeId id = NodeId.of(nodeId);
        if (nodeRepository.findById(id).isEmpty()) {
            throw new NodeNotFoundException(nodeId);
        }
        return snapshotRepository.findByNodeId(id, tenantId);
    }

    @Transactional(readOnly = true)
    public List<InventorySnapshot> getSnapshotBySku(String sku, String tenantId) {
        return snapshotRepository.findBySku(Sku.of(sku), tenantId);
    }

    @Transactional(readOnly = true)
    public List<NodeStaleness> getStaleness(String tenantId) {
        return stalenessRepository.findAllByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public List<InventoryNode> getNodes(String tenantId) {
        return nodeRepository.findAllByTenantId(tenantId);
    }

    // -------------------------------------------------------------------------
    // Staleness detection (called by StalenessDetectionScheduler)
    // -------------------------------------------------------------------------

    /**
     * Detect stale nodes and publish SNAPSHOT_STALE alerts.
     * batch-heavy first code: called by ShedLock-protected @Scheduled method.
     */
    @Transactional
    public void detectAndAlertStaleNodes(String tenantId) {
        StalenessThreshold threshold = StalenessThreshold.ofSeconds(thresholdSeconds);
        Instant now = clock.now();
        List<NodeStaleness> allStaleness = stalenessRepository.findAllByTenantId(tenantId);

        int alertsPublished = 0;
        for (NodeStaleness ns : allStaleness) {
            boolean statusChanged = ns.evaluate(threshold, now);
            stalenessRepository.save(ns);

            if (ns.isStale() && statusChanged) {
                // Only alert on transition to STALE (statusChanged=true) to avoid alert spam
                alertPublisherPort.publishStalenessAlert(
                        ns.getNodeId(), tenantId, ns.getStalenessStatus(), now);
                alertsPublished++;
            }
        }
        log.info("staleness detection complete: tenantId={} checked={} alertsPublished={}",
                tenantId, allStaleness.size(), alertsPublished);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Applies a quantity delta to the inventory snapshot for the given node/SKU pair.
     * If no snapshot exists yet, a new one is created:
     * <ul>
     *   <li>For an addition: initialised with the given quantity.</li>
     *   <li>For a subtraction: initialised at zero (no negative inventory).</li>
     * </ul>
     * Extracted from three duplicated 5-step blocks in {@code applyInventoryReceived},
     * {@code applyInventoryAdjusted}, and {@code applyInventoryTransferred}
     * (TASK-SCM-BE-016 L5+L6).
     */
    private void applySnapshotDelta(NodeId nodeId, Sku sku, Quantity delta,
                                    boolean isAddition, UUID eventId,
                                    Instant occurredAt, String tenantId) {
        Optional<InventorySnapshot> existing =
                snapshotRepository.findByNodeIdAndSku(nodeId, sku, tenantId);
        if (existing.isPresent()) {
            existing.get().applyDelta(delta, isAddition, eventId, occurredAt);
            snapshotRepository.save(existing.get());
        } else {
            Quantity initial = isAddition ? delta : Quantity.ZERO;
            InventorySnapshot snapshot =
                    InventorySnapshot.create(nodeId, sku, tenantId, initial, eventId, occurredAt);
            snapshotRepository.save(snapshot);
        }
    }

    /**
     * Resolve the node for {@code externalId}, auto-registering it on first event
     * (Edge Case 3).
     *
     * <p>ADR-MONO-050 D9: an existing node learns the wms {@code warehouseCode}
     * set-if-present — a {@code null} incoming code (wms's warehouse master snapshot
     * not yet populated) is ignored rather than wiping a previously stored code, and
     * the extra write is skipped when the value is unchanged.
     */
    private InventoryNode resolveOrCreateNode(String externalId, NodeType type,
                                              String tenantId, String warehouseCode) {
        Optional<InventoryNode> existing =
                nodeRepository.findByTenantIdAndExternalId(tenantId, externalId);
        if (existing.isPresent()) {
            InventoryNode node = existing.get();
            if (node.applyWarehouseCodeIfPresent(warehouseCode, clock.now())) {
                log.info("node warehouseCode updated: externalId={} tenant={} warehouseCode={}",
                        externalId, tenantId, warehouseCode);
                return nodeRepository.save(node);
            }
            return node;
        }
        // Edge Case 3: auto-register node on first event
        log.info("auto-registering node: externalId={} type={} tenant={} warehouseCode={}",
                externalId, type, tenantId, warehouseCode);
        InventoryNode newNode = InventoryNode.autoRegisterWmsWarehouse(
                NodeId.of(UUID.randomUUID()), tenantId, externalId, warehouseCode, clock.now());
        return nodeRepository.save(newNode);
    }

    private void updateStaleness(NodeId nodeId, String tenantId, UUID eventId, Instant eventAt) {
        NodeStaleness staleness = stalenessRepository.findByNodeId(nodeId)
                .orElseGet(() -> NodeStaleness.create(nodeId, tenantId, eventAt, eventId));
        staleness.recordEventReceived(eventId, eventAt);
        stalenessRepository.save(staleness);
    }
}
