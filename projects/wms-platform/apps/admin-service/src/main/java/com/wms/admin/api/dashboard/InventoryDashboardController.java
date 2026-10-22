package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.InventorySnapshotResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.application.settings.LowStockThresholdReader;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotId;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 1.1}. Low-stock ({@code lowStockFlag} and the
 * {@code lowStockOnly} filter) is derived per request from the current
 * {@code inventory.low_stock.default_threshold_qty} setting — TASK-BE-644. The
 * list endpoint reads the threshold once, so the filter and the flags in one
 * response always agree.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/inventory")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class InventoryDashboardController {

    private static final String DEFAULT_SORT = "lastEventAt,desc";

    private final InventorySnapshotRepository repository;
    private final LowStockThresholdReader thresholdReader;

    public InventoryDashboardController(InventorySnapshotRepository repository,
                                        LowStockThresholdReader thresholdReader) {
        this.repository = repository;
        this.thresholdReader = thresholdReader;
    }

    @GetMapping
    public PageResponse<InventorySnapshotResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false, defaultValue = "false") boolean lowStockOnly,
            @RequestParam(required = false) Integer minOnHand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        int threshold = thresholdReader.currentThreshold();
        Page<InventorySnapshotEntity> result = repository.search(warehouseId, locationId, skuId,
                lotId, lowStockOnly, threshold, minOnHand,
                PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, e -> InventorySnapshotResponse.from(e, threshold));
    }

    @GetMapping("/by-key")
    public ResponseEntity<InventorySnapshotResponse> getByKey(
            @RequestParam UUID locationId,
            @RequestParam UUID skuId,
            @RequestParam(required = false) UUID lotId) {
        InventorySnapshotId id = new InventorySnapshotId(locationId, skuId, lotId);
        return repository.findById(id)
                .map(e -> InventorySnapshotResponse.from(e, thresholdReader.currentThreshold()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
