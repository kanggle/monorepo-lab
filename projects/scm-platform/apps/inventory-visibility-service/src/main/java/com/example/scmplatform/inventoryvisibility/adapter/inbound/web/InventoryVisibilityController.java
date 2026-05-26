package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.PageResponse;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.SkuBreakdownResponse;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.SnapshotResponse;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.SkuBreakdownCachePort;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.staleness.NodeStaleness;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Read-only REST API for cross-node inventory snapshot queries.
 * <p>
 * Endpoints per specs/contracts/http/inventory-visibility-api.md:
 * <ul>
 *   <li>GET /api/inventory-visibility/snapshot — cross-node paginated list</li>
 *   <li>GET /api/inventory-visibility/snapshot?nodeId={id} — single-node snapshot</li>
 * </ul>
 * All responses include {@code meta.staleness} and {@code meta.warning} (S5).
 */
@RestController
@RequestMapping("/api/inventory-visibility")
@RequiredArgsConstructor
public class InventoryVisibilityController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final InventoryVisibilityApplicationService applicationService;
    private final SkuBreakdownCachePort cache;

    /**
     * GET /api/inventory-visibility/snapshot
     * <p>
     * Without nodeId: paginated cross-node list.
     * With nodeId: all snapshots for a specific node.
     */
    @GetMapping("/snapshot")
    public ResponseEntity<ApiEnvelope<Object>> getSnapshot(
            @RequestParam(required = false) String nodeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {

        String tenantId = TenantClaimExtractor.extractTenantId(jwt);
        List<NodeStaleness> allStaleness = applicationService.getStaleness(tenantId);
        Map<String, StalenessStatus> stalenessMap = buildStalenessMap(allStaleness);

        if (nodeId != null && !nodeId.isBlank()) {
            // Single-node snapshot list
            List<InventorySnapshot> snapshots = applicationService.getSnapshotByNode(nodeId, tenantId);
            List<SnapshotResponse> responses = snapshots.stream()
                    .map(s -> SnapshotResponse.from(s, stalenessMap.get(s.getNodeId().toString())))
                    .toList();
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("nodeId", nodeId);
            meta.put("count", responses.size());
            meta.put("staleness", buildOverallStaleness(stalenessMap, nodeId));
            return ResponseEntity.ok(ApiEnvelope.of(responses, meta));
        }

        // Cross-node paginated list
        int clampedSize = Math.min(size, DEFAULT_PAGE_SIZE * 5);
        List<InventorySnapshot> snapshots = applicationService.getCrossNodeSnapshot(tenantId, page, clampedSize);
        long total = applicationService.countCrossNodeSnapshot(tenantId);
        List<SnapshotResponse> responses = snapshots.stream()
                .map(s -> SnapshotResponse.from(s, stalenessMap.get(s.getNodeId().toString())))
                .toList();
        PageResponse<SnapshotResponse> pageResponse =
                PageResponse.of(responses, page, clampedSize, total);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("staleness", buildOverallStaleness(stalenessMap, null));
        return ResponseEntity.ok(ApiEnvelope.of(pageResponse, meta));
    }

    /**
     * GET /api/inventory-visibility/sku/{sku}
     * SKU cross-node breakdown with optional Redis cache (Acceptance Criteria #16).
     */
    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiEnvelope<SkuBreakdownResponse>> getSkuSnapshot(
            @org.springframework.web.bind.annotation.PathVariable String sku,
            @AuthenticationPrincipal Jwt jwt) {

        String tenantId = TenantClaimExtractor.extractTenantId(jwt);
        String cacheStatus = "MISS";

        // Try Redis cache first (fail-open — Acceptance Criteria #16)
        Optional<SkuBreakdownResponse> cached =
                cache.get(sku, tenantId, new TypeReference<>() {});
        if (cached.isPresent()) {
            cacheStatus = "HIT";
            return ResponseEntity.ok()
                    .header("X-Cache", cacheStatus)
                    .body(ApiEnvelope.of(cached.get()));
        }
        if (!cache.isAvailable()) {
            cacheStatus = "UNAVAILABLE";
        }

        List<InventorySnapshot> snapshots = applicationService.getSnapshotBySku(sku, tenantId);
        List<NodeStaleness> allStaleness = applicationService.getStaleness(tenantId);
        Map<String, StalenessStatus> stalenessMap = buildStalenessMap(allStaleness);

        SkuBreakdownResponse breakdown = SkuBreakdownResponse.from(
                sku, snapshots,
                nodeId -> {
                    StalenessStatus s = stalenessMap.get(nodeId);
                    return s != null ? s.name() : StalenessStatus.FRESH.name();
                });

        // Cache only when Redis is available
        if (!"UNAVAILABLE".equals(cacheStatus)) {
            cache.put(sku, tenantId, breakdown);
        }

        return ResponseEntity.ok()
                .header("X-Cache", cacheStatus)
                .body(ApiEnvelope.of(breakdown));
    }

    /**
     * GET /api/inventory-visibility/nodes — node list + status.
     */
    @GetMapping("/nodes")
    public ResponseEntity<ApiEnvelope<List<com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.NodeResponse>>> getNodes(
            @AuthenticationPrincipal Jwt jwt) {
        String tenantId = TenantClaimExtractor.extractTenantId(jwt);
        List<com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.NodeResponse> nodes =
                applicationService.getNodes(tenantId).stream()
                        .map(com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.NodeResponse::from)
                        .toList();
        return ResponseEntity.ok(ApiEnvelope.of(nodes));
    }

    private Map<String, StalenessStatus> buildStalenessMap(List<NodeStaleness> list) {
        return list.stream()
                .collect(Collectors.toMap(
                        ns -> ns.getNodeId().toString(),
                        NodeStaleness::getStalenessStatus
                ));
    }

    private String buildOverallStaleness(Map<String, StalenessStatus> map, String singleNodeId) {
        if (singleNodeId != null) {
            StalenessStatus s = map.get(singleNodeId);
            return s != null ? s.name() : StalenessStatus.FRESH.name();
        }
        boolean anyStale = map.values().stream()
                .anyMatch(s -> s == StalenessStatus.STALE || s == StalenessStatus.UNREACHABLE);
        return anyStale ? "STALE_NODES_PRESENT" : "ALL_FRESH";
    }
}
