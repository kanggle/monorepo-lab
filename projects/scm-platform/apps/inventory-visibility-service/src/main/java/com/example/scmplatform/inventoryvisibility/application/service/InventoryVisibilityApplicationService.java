package com.example.scmplatform.inventoryvisibility.application.service;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.AlertPublisherPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ClockPort;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeNotFoundException;
import com.example.scmplatform.inventoryvisibility.domain.error.NodeTypeConflictException;
import com.example.scmplatform.inventoryvisibility.domain.expectation.InboundExpectation;
import com.example.scmplatform.inventoryvisibility.domain.expectation.repository.InboundExpectationRepository;
import com.example.scmplatform.inventoryvisibility.domain.node.InventoryNode;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeId;
import com.example.scmplatform.inventoryvisibility.domain.node.NodeType;
import com.example.scmplatform.inventoryvisibility.domain.node.repository.InventoryNodeRepository;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Quantity;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.Sku;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.repository.InventorySnapshotRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.LocalDate;
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
 *   <li>Idempotency: duplicate eventId check via ProcessedEventPort before any mutation.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryVisibilityApplicationService {

    private final InventoryNodeRepository nodeRepository;
    private final InventorySnapshotRepository snapshotRepository;
    private final NodeStalenessRepository stalenessRepository;
    private final InboundExpectationRepository inboundExpectationRepository;
    private final ProcessedEventPort processedEventPort;
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
        if (processedEventPort.isDuplicate(eventId)) {
            log.debug("Duplicate event skipped: eventId={} topic={}", eventId, sourceTopic);
            return;
        }
        InventoryNode node = resolveOrCreateNode(warehouseId, NodeType.WMS_WAREHOUSE, tenantId, warehouseCode);
        applySnapshotDelta(node.getId(), Sku.of(skuId),
                Quantity.of(BigDecimal.valueOf(qtyReceived)), true,
                eventId, occurredAt, tenantId);
        updateStaleness(node.getId(), tenantId, eventId, occurredAt);
        processedEventPort.markProcessed(eventId, tenantId, clock.now(), sourceTopic);
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
        if (processedEventPort.isDuplicate(eventId)) {
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
        processedEventPort.markProcessed(eventId, tenantId, clock.now(), sourceTopic);
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
        if (processedEventPort.isDuplicate(eventId)) {
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
        processedEventPort.markProcessed(eventId, tenantId, clock.now(), sourceTopic);
        log.info("applied inventory.transferred: src={} dst={} sku={} qty={} eventId={}",
                srcNode.getId(), dstNode.getId(), skuId, quantity, eventId);
    }

    // -------------------------------------------------------------------------
    // 3PL observation ingestion use case (ADR-MONO-054 §D4 / TASK-SCM-BE-047)
    // -------------------------------------------------------------------------

    /**
     * Record an operator-pushed observation of stock held at an existing
     * {@code THIRD_PARTY_LOGISTICS} node — {@code POST .../nodes/{nodeId}/observed-stock}
     * (ADR-MONO-054 §D4 / TASK-SCM-BE-047).
     *
     * <p>Unlike the wms {@code applyInventory*} use cases above, this is a REST push,
     * not a Kafka event, and the observation is an **absolute** reading (a full snapshot
     * of what the 3PL reports), never a delta — {@link InventorySnapshot#applyQuantity}
     * is used, not {@link InventorySnapshot#applyDelta} / {@link #applySnapshotDelta}.
     *
     * <p>The node must already exist as {@code THIRD_PARTY_LOGISTICS} for the calling
     * tenant — {@link #resolveOrCreateNode} (which auto-registers a {@code WMS_WAREHOUSE})
     * is deliberately never called here (Failure Scenario B).
     *
     * @throws NodeNotFoundException     if {@code nodeId} does not resolve to any node
     * @throws NodeTypeConflictException if the resolved node is not
     *                                    {@code THIRD_PARTY_LOGISTICS}, or belongs to a
     *                                    different tenant than {@code tenantId}
     */
    @Transactional
    public void applyThirdPartyObservedStock(String nodeId, String tenantId,
                                              Instant observedAt, List<ObservedLine> lines) {
        NodeId id = NodeId.of(nodeId);
        InventoryNode node = nodeRepository.findById(id)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        if (node.getNodeType() != NodeType.THIRD_PARTY_LOGISTICS) {
            throw new NodeTypeConflictException("Inventory node nodeId=" + nodeId
                    + " has type=" + node.getNodeType()
                    + "; observed-stock ingestion requires THIRD_PARTY_LOGISTICS");
        }
        if (!node.getTenantId().equals(tenantId)) {
            throw new NodeTypeConflictException(
                    "Inventory node nodeId=" + nodeId + " does not belong to tenant=" + tenantId);
        }

        // One observation id per push (not per line) — every line in this call shares the
        // same provenance marker, mirroring how a single wms event id covers its payload.
        UUID observationId = UUID.randomUUID();
        for (ObservedLine line : lines) {
            Sku sku = Sku.of(line.skuCode());
            Quantity effective = applyObservedQuantity(id, sku,
                    Quantity.of(line.quantity()), observationId, observedAt, tenantId);
            // ADR-MONO-055 §D4 / TASK-SCM-BE-049: reconcile any OPEN 3PL inbound expectation
            // for this (node, sku) now that the stock has been observed — reuse the
            // observation path rather than a bespoke scheduler.
            reconcileInboundExpectations(id, sku, effective, tenantId);
        }
        // The only path that seeds/refreshes NodeStaleness for a 3PL node — registration
        // (RegisterThirdPartyLogisticsNodeService) does not create one (Edge Case: Staleness
        // seed timing, TASK-SCM-BE-047).
        updateStaleness(id, tenantId, observationId, observedAt);
        log.info("applied 3PL observed stock: node={} lines={} observationId={} observedAt={}",
                id, lines.size(), observationId, observedAt);
    }

    /** A single 3PL observed-stock reading: an absolute quantity for one SKU. */
    public record ObservedLine(String skuCode, BigDecimal quantity) {
    }

    // -------------------------------------------------------------------------
    // 3PL inbound-expectation sink (ADR-MONO-055 §D4 / TASK-SCM-BE-049)
    // -------------------------------------------------------------------------

    /**
     * Record the scm-internal <b>3PL inbound expectation</b> honour sink
     * (ADR-MONO-055 §D4 / TASK-SCM-BE-049) — one {@code inbound_expectations} row per
     * line — for a {@code THIRD_PARTY_LOGISTICS}-addressed replenishment PO that
     * {@code procurement-service} confirmed
     * ({@code scm.procurement.inbound-expected.third-party.v1}).
     *
     * <p>Fail-closed on the addressed node (Edge Case: 3PL node deregistered/absent):
     * the node must exist, be {@code THIRD_PARTY_LOGISTICS}, and belong to the caller's
     * tenant — otherwise a clear error is thrown (routed to the topic DLT by the
     * consumer) and <b>no orphan expectation</b> is created.
     *
     * <p>Idempotent on the PO reference (Edge Case: duplicate PO / re-confirm): a line
     * whose {@code (tenant, poNumber, sku, node)} already exists is skipped; the DB
     * UNIQUE constraint is the concurrent-replay backstop.
     *
     * @throws NodeNotFoundException     if {@code nodeId} resolves to no node
     * @throws NodeTypeConflictException if the node is not {@code THIRD_PARTY_LOGISTICS}
     *                                    or belongs to another tenant
     */
    @Transactional
    public void recordThirdPartyInboundExpectation(String nodeId, String tenantId,
                                                    String sourcePoId, String sourcePoNumber,
                                                    LocalDate expectedAt, List<ExpectedLine> lines) {
        NodeId id = NodeId.of(nodeId);
        InventoryNode node = nodeRepository.findById(id)
                .orElseThrow(() -> new NodeNotFoundException(nodeId));
        if (node.getNodeType() != NodeType.THIRD_PARTY_LOGISTICS) {
            throw new NodeTypeConflictException("Inventory node nodeId=" + nodeId
                    + " has type=" + node.getNodeType()
                    + "; 3PL inbound-expectation sink requires THIRD_PARTY_LOGISTICS");
        }
        if (!node.getTenantId().equals(tenantId)) {
            throw new NodeTypeConflictException(
                    "Inventory node nodeId=" + nodeId + " does not belong to tenant=" + tenantId);
        }

        Instant now = clock.now();
        for (ExpectedLine line : lines) {
            Sku sku = Sku.of(line.skuCode());
            if (inboundExpectationRepository.exists(tenantId, sourcePoNumber, sku, id)) {
                log.debug("3PL inbound-expectation idempotent skip: po={} sku={} node={}",
                        sourcePoNumber, sku, id);
                continue;
            }
            InboundExpectation expectation = InboundExpectation.record(
                    tenantId, id, sku, Quantity.of(line.expectedQuantity()),
                    sourcePoId, sourcePoNumber, expectedAt, now);
            try {
                inboundExpectationRepository.save(expectation);
                log.info("recorded 3PL inbound-expectation: po={} sku={} qty={} node={} expectedAt={}",
                        sourcePoNumber, sku, line.expectedQuantity(), id, expectedAt);
            } catch (DataIntegrityViolationException race) {
                // Concurrent replay won the UNIQUE (tenant, po_number, sku, node) — idempotent
                // no-op, exactly as the exists() pre-check would have skipped it.
                log.debug("3PL inbound-expectation race (idempotent): po={} sku={} node={}",
                        sourcePoNumber, sku, id);
            }
        }
    }

    /** A single expected-inbound line: an ordered quantity for one SKU. */
    public record ExpectedLine(String skuCode, BigDecimal expectedQuantity) {
    }

    /**
     * Reconcile OPEN 3PL inbound expectations for a {@code (node, sku)} pair against an
     * observed absolute quantity (ADR-MONO-055 §D4 / TASK-SCM-BE-049). v1 is binary: an
     * expectation whose expected quantity is met/exceeded by the observation is marked
     * SATISFIED; an unmet one stays OPEN (a visible, aging operational signal).
     */
    private void reconcileInboundExpectations(NodeId nodeId, Sku sku, Quantity observed, String tenantId) {
        List<InboundExpectation> open =
                inboundExpectationRepository.findOpenByNodeAndSku(nodeId, sku, tenantId);
        if (open.isEmpty()) {
            return;
        }
        Instant now = clock.now();
        for (InboundExpectation expectation : open) {
            if (expectation.isSatisfiedBy(observed)) {
                expectation.markSatisfied(now);
                inboundExpectationRepository.save(expectation);
                log.info("reconciled 3PL inbound-expectation SATISFIED: po={} sku={} node={} observed={}",
                        expectation.getSourcePoNumber(), sku, nodeId, observed);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Query use cases (called by REST controllers)
    // -------------------------------------------------------------------------

    /**
     * Cross-node paginated search, sourced from the shared {@link PageQuery}/{@link PageResult}
     * carrier (ADR-MONO-058 § D3 / TASK-SCM-BE-056). Replaces the previous
     * {@code getCrossNodeSnapshot(tenantId, page, size)} + {@code countCrossNodeSnapshot(tenantId)}
     * pair — {@link PageResult} carries {@code totalPages}, which the old pair never computed.
     */
    @Transactional(readOnly = true)
    public PageResult<InventorySnapshot> getCrossNodeSnapshot(String tenantId, PageQuery pageQuery) {
        return snapshotRepository.search(tenantId, pageQuery);
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
     * node's business {@code warehouseCode} (ADR-MONO-050 D9 / TASK-SCM-BE-037) <b>and</b>
     * its {@code nodeType} (ADR-MONO-055 §D2/§D3 / TASK-SCM-BE-048) so the demand-planning
     * batch sweep can address a replenishment PO by code and widen its target from
     * "wms warehouse only" to "any observed node" (a {@code THIRD_PARTY_LOGISTICS} node
     * observed read-only via TASK-SCM-BE-047 can now be a replenishment target).
     *
     * <p>Both dimensions are nullable and share the same defensive handling: the owning
     * node may not have learned a {@code warehouseCode} yet (wms emits it best-effort), and
     * a node absent from the registry resolves to a {@code null} type. A null on either
     * never omits the row — a null code only skips the downstream inbound-expected
     * addressing, and a null type is read as {@code WMS_WAREHOUSE} downstream (backward
     * compat).
     */
    @Transactional(readOnly = true)
    public List<SnapshotWithNodeMeta> getAllSnapshotsAcrossTenantsWithWarehouseCode() {
        // Snapshots are keyed per (node, sku), so ONE node backs many rows and a naive
        // per-row lookup re-reads the same node repeatedly. Memoise for the duration of this
        // read: the projection then costs one lookup per DISTINCT node instead of one per
        // snapshot row (the sweep reads every row). Deliberately local — no batch-fetch method
        // is added to the node port for a read concern, and a call-scoped map cannot go stale.
        // NOTE: containsKey/put, not computeIfAbsent — computeIfAbsent does NOT record a null
        // result, so it would re-query on every row for exactly the nodes whose metadata is
        // still null (the initial / fail-closed state). Caching the null is the whole point.
        Map<NodeId, NodeMeta> metaByNode = new HashMap<>();
        return snapshotRepository.findAllAcrossTenants().stream()
                .map(s -> {
                    NodeId nodeId = s.getNodeId();
                    if (!metaByNode.containsKey(nodeId)) {
                        metaByNode.put(nodeId, nodeRepository.findById(nodeId)
                                .map(n -> new NodeMeta(n.getWarehouseCode(),
                                        n.getNodeType() != null ? n.getNodeType().name() : null))
                                .orElse(new NodeMeta(null, null)));
                    }
                    NodeMeta meta = metaByNode.get(nodeId);
                    return new SnapshotWithNodeMeta(s, meta.warehouseCode(), meta.nodeType());
                })
                .toList();
    }

    /** Memoisation carrier for a node's nullable warehouse code + node type. */
    private record NodeMeta(String warehouseCode, String nodeType) {
    }

    /**
     * Read-projection pairing a snapshot with its node's nullable warehouse code and
     * nullable node type ({@code WMS_WAREHOUSE} | {@code SUPPLIER} |
     * {@code THIRD_PARTY_LOGISTICS} | {@code IN_TRANSIT}; null when the node is absent).
     */
    public record SnapshotWithNodeMeta(InventorySnapshot snapshot, String warehouseCode,
                                       String nodeType) {
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
     * Applies an **absolute** observed quantity to the inventory snapshot for a 3PL
     * node/SKU pair (ADR-MONO-054 §D4 / TASK-SCM-BE-047) — mirrors the structure of
     * {@link #applySnapshotDelta} but calls {@link InventorySnapshot#applyQuantity}
     * (set) rather than {@link InventorySnapshot#applyDelta} (accumulate), because a
     * 3PL observation is a full reading, not an incremental wms event.
     *
     * <p>Ordering guard: if the existing snapshot's {@code lastEventAt} is strictly
     * newer than this observation's {@code observedAt}, the line is skipped — a
     * stale/replayed reading must never overwrite a newer one (Edge Case: Stale
     * reading). A zero quantity is a valid observation (SKU dropped to 0) and is
     * applied like any other value, never treated as "no observation".
     */
    private Quantity applyObservedQuantity(NodeId nodeId, Sku sku, Quantity observedQuantity,
                                           UUID observationId, Instant observedAt, String tenantId) {
        Optional<InventorySnapshot> existing =
                snapshotRepository.findByNodeIdAndSku(nodeId, sku, tenantId);
        if (existing.isPresent()) {
            InventorySnapshot snapshot = existing.get();
            if (snapshot.getLastEventAt().isAfter(observedAt)) {
                log.debug("skipping stale 3PL observation: node={} sku={} storedLastEventAt={} observedAt={}",
                        nodeId, sku, snapshot.getLastEventAt(), observedAt);
                // The stored (newer) quantity is authoritative for reconciliation — a stale
                // reading must not decide expectation satisfaction with an out-of-date value.
                return snapshot.getQuantity();
            }
            snapshot.applyQuantity(observedQuantity, observationId, observedAt);
            snapshotRepository.save(snapshot);
            return observedQuantity;
        }
        InventorySnapshot snapshot = InventorySnapshot.create(
                nodeId, sku, tenantId, observedQuantity, observationId, observedAt);
        snapshotRepository.save(snapshot);
        return observedQuantity;
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
