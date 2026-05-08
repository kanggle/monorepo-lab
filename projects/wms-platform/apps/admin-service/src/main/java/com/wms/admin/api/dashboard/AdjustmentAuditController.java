package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.AdjustmentAuditResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.readmodel.inventory.AdjustmentAuditEntity;
import com.wms.admin.readmodel.inventory.AdjustmentAuditRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code admin-service-api.md § 1.5}. Append-only — no PATCH/DELETE. */
@RestController
@RequestMapping("/api/v1/admin/dashboard/adjustments")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class AdjustmentAuditController {

    private static final String DEFAULT_SORT = "occurredAt,desc";

    private final AdjustmentAuditRepository repository;

    public AdjustmentAuditController(AdjustmentAuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public PageResponse<AdjustmentAuditResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID skuId,
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant occurredAtTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        DateRangeSupport.validate("occurredAt", occurredAtFrom, occurredAtTo);
        Page<AdjustmentAuditEntity> result = repository.search(warehouseId, locationId, skuId,
                bucket, reasonCode, occurredAtFrom, occurredAtTo,
                PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, AdjustmentAuditResponse::from);
    }
}
