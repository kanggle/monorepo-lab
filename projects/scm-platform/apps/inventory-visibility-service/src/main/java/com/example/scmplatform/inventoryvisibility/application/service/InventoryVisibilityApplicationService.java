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
import java.util.List;
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
                                        long qtyReceived, UUID eventId,
                                        Instant occurredAt, String tenantId,
                                        String sourceTopic) {
        if (eventDedupePort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }

        InventoryNode node = resolveOrCreateNode(warehouseId, NodeType.WMS_WAREHOUSE, tenantId);
        Sku sku = Sku.of(skuId);
        Quantity quantity = Quantity.of(BigDecimal.valueOf(qtyReceived));

        Optional<InventorySnapshot> existing =
                snapshotRepository.findByNodeIdAndSku(node.getId(), sku, tenantId);
        if (existing.isPresent()) {
            existing.get().applyDelta(quantity, true, eventId, occurredAt);
            snapshotRepository.save(existing.get());
        } else {
            InventorySnapshot snapshot =
                    InventorySnapshot.create(node.getId(), sku, tenantId, quantity, eventId, occurredAt);
            snapshotRepository.save(snapshot);
        }

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
                                        long delta, UUID eventId,
                                        Instant occurredAt, String tenantId,
                                        String sourceTopic) {
        if (eventDedupePort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }

        InventoryNode node = resolveOrCreateNode(warehouseId, NodeType.WMS_WAREHOUSE, tenantId);
        Sku sku = Sku.of(skuId);

        Optional<InventorySnapshot> existing =
                snapshotRepository.findByNodeIdAndSku(node.getId(), sku, tenantId);
        Quantity absDelta = Quantity.of(BigDecimal.valueOf(Math.abs(delta)));
        boolean isAddition = delta >= 0;

        if (existing.isPresent()) {
            existing.get().applyDelta(absDelta, isAddition, eventId, occurredAt);
            snapshotRepository.save(existing.get());
        } else {
            // Node exists in wms but we have no snapshot yet — create with zero base
            Quantity initial = isAddition ? absDelta : Quantity.ZERO;
            InventorySnapshot snapshot =
                    InventorySnapshot.create(node.getId(), sku, tenantId, initial, eventId, occurredAt);
            snapshotRepository.save(snapshot);
        }

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
                                           UUID eventId, Instant occurredAt,
                                           String tenantId, String sourceTopic) {
        if (eventDedupePort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }

        InventoryNode srcNode = resolveOrCreateNode(sourceWarehouseId, NodeType.WMS_WAREHOUSE, tenantId);
        InventoryNode dstNode = resolveOrCreateNode(destWarehouseId, NodeType.WMS_WAREHOUSE, tenantId);
        Sku sku = Sku.of(skuId);
        Quantity qty = Quantity.of(BigDecimal.valueOf(quantity));

        // Decrement source
        Optional<InventorySnapshot> srcSnap =
                snapshotRepository.findByNodeIdAndSku(srcNode.getId(), sku, tenantId);
        if (srcSnap.isPresent()) {
            srcSnap.get().applyDelta(qty, false, eventId, occurredAt);
            snapshotRepository.save(srcSnap.get());
        } else {
            snapshotRepository.save(
                    InventorySnapshot.create(srcNode.getId(), sku, tenantId, Quantity.ZERO, eventId, occurredAt));
        }

        // Increment destination
        Optional<InventorySnapshot> dstSnap =
                snapshotRepository.findByNodeIdAndSku(dstNode.getId(), sku, tenantId);
        if (dstSnap.isPresent()) {
            dstSnap.get().applyDelta(qty, true, eventId, occurredAt);
            snapshotRepository.save(dstSnap.get());
        } else {
            snapshotRepository.save(
                    InventorySnapshot.create(dstNode.getId(), sku, tenantId, qty, eventId, occurredAt));
        }

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

    private InventoryNode resolveOrCreateNode(String externalId, NodeType type, String tenantId) {
        return nodeRepository.findByTenantIdAndExternalId(tenantId, externalId)
                .orElseGet(() -> {
                    // Edge Case 3: auto-register node on first event
                    log.info("auto-registering node: externalId={} type={} tenant={}", externalId, type, tenantId);
                    InventoryNode newNode = InventoryNode.autoRegisterWmsWarehouse(
                            NodeId.of(UUID.randomUUID()), tenantId, externalId, clock.now());
                    return nodeRepository.save(newNode);
                });
    }

    private void updateStaleness(NodeId nodeId, String tenantId, UUID eventId, Instant eventAt) {
        NodeStaleness staleness = stalenessRepository.findByNodeId(nodeId)
                .orElseGet(() -> NodeStaleness.create(nodeId, tenantId, eventAt, eventId));
        staleness.recordEventReceived(eventId, eventAt);
        stalenessRepository.save(staleness);
    }
}
