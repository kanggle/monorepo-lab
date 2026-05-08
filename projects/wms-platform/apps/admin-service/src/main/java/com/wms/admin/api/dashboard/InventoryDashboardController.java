package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.InventorySnapshotResponse;
import com.wms.admin.api.dto.PageResponse;
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

/** {@code admin-service-api.md § 1.1}. */
@RestController
@RequestMapping("/api/v1/admin/dashboard/inventory")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class InventoryDashboardController {

    private static final String DEFAULT_SORT = "lastEventAt,desc";

    private final InventorySnapshotRepository repository;

    public InventoryDashboardController(InventorySnapshotRepository repository) {
        this.repository = repository;
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
        Page<InventorySnapshotEntity> result = repository.search(warehouseId, locationId, skuId,
                lotId, lowStockOnly, minOnHand, PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, InventorySnapshotResponse::from);
    }

    @GetMapping("/by-key")
    public ResponseEntity<InventorySnapshotResponse> getByKey(
            @RequestParam UUID locationId,
            @RequestParam UUID skuId,
            @RequestParam(required = false) UUID lotId) {
        InventorySnapshotId id = new InventorySnapshotId(locationId, skuId, lotId);
        return repository.findById(id)
                .map(InventorySnapshotResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
