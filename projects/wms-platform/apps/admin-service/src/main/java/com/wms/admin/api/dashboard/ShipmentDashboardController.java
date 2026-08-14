package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.ShipmentSummaryResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.application.security.ReadScopeProvider;
import com.wms.admin.readmodel.outbound.ShipmentSummaryEntity;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 1.3 (shipments)}.
 *
 * <p>One of the two <b>tenant-owned</b> dashboards (§ 1.0, ADR-MONO-065 § D1). The
 * tenant filter is ANDed with the caller's own filters, so narrowing by
 * {@code ?orderId=} on another tenant's order returns an empty page rather than that
 * tenant's shipment.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/shipments")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class ShipmentDashboardController {

    private static final String DEFAULT_SORT = "shippedAt,desc";

    private final ShipmentSummaryRepository repository;
    private final ReadScopeProvider readScopeProvider;

    public ShipmentDashboardController(ShipmentSummaryRepository repository,
                                       ReadScopeProvider readScopeProvider) {
        this.repository = repository;
        this.readScopeProvider = readScopeProvider;
    }

    @GetMapping
    public PageResponse<ShipmentSummaryResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant shippedAtFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant shippedAtTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        DateRangeSupport.validate("shippedAt", shippedAtFrom, shippedAtTo);
        Page<ShipmentSummaryEntity> result = repository.search(
                readScopeProvider.current().tenantFilter(),
                warehouseId, orderId, carrierCode,
                shippedAtFrom, shippedAtTo, PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, ShipmentSummaryResponse::from);
    }
}
