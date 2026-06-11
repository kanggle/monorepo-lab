package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto.InternalSnapshotResponse;
import com.example.scmplatform.inventoryvisibility.application.service.InventoryVisibilityApplicationService;
import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Internal, network-trusted snapshot read for the demand-planning replenishment
 * batch (ADR-MONO-027 §D7.1).
 *
 * <p><b>No authentication</b> ({@code permitAll} in {@code SecurityConfig}) — the
 * caller is the unattended {@code ReorderSweepScheduler}, which has no operator
 * token (scm has no workload-identity infra). The trust boundary is network
 * isolation only: this {@code /internal/**} path is NOT routed by scm-gateway
 * (which routes only {@code /api/v1/**}) and must never be exposed on a public
 * host route. It returns the snapshot <b>across all tenants</b> (the batch is
 * tenant-agnostic — demand-planning raises suggestions under the static {@code scm}
 * slug, exactly as the live alert path does).
 */
@RestController
@RequestMapping("/internal/inventory-visibility")
@RequiredArgsConstructor
public class InternalSnapshotController {

    private final InventoryVisibilityApplicationService applicationService;

    @GetMapping("/snapshot")
    public ResponseEntity<ApiEnvelope<List<InternalSnapshotResponse>>> getSnapshotAcrossTenants() {
        List<InventorySnapshot> snapshots = applicationService.getAllSnapshotsAcrossTenants();
        List<InternalSnapshotResponse> data = snapshots.stream()
                .map(InternalSnapshotResponse::from)
                .toList();
        return ResponseEntity.ok(ApiEnvelope.of(data, Map.of("count", data.size())));
    }
}
