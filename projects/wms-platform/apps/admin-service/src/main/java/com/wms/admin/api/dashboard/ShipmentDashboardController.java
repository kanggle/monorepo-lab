package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.ShipmentSummaryResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.readmodel.outbound.ShipmentSummaryEntity;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code admin-service-api.md § 1.3 (shipments)}. */
@RestController
@RequestMapping("/api/v1/admin/dashboard/shipments")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class ShipmentDashboardController {

    private static final String DEFAULT_SORT = "shippedAt,desc";

    private final ShipmentSummaryRepository repository;

    public ShipmentDashboardController(ShipmentSummaryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<ShipmentSummaryResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        Page<ShipmentSummaryEntity> result = repository.search(warehouseId, orderId, carrierCode,
                PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, ShipmentSummaryResponse::from);
    }
}
