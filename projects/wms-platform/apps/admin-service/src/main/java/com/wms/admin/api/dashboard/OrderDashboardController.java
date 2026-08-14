package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.OrderSummaryResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.application.security.ReadScopeProvider;
import com.wms.admin.readmodel.outbound.OrderSummaryEntity;
import com.wms.admin.readmodel.outbound.OrderSummaryRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 1.3 (orders)}.
 *
 * <p>One of the two <b>tenant-owned</b> dashboards (§ 1.0, ADR-MONO-065 § D1): a
 * tenant-scoped caller sees only its own orders. {@code @PreAuthorize} answers
 * "may this caller reach the endpoint"; {@link ReadScopeProvider} answers "which rows
 * does it get" — the second question is the one this surface used not to ask, while
 * the raw {@code /api/v1/outbound/orders} plane did.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/orders")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class OrderDashboardController {

    private static final String DEFAULT_SORT = "receivedAt,desc";

    private final OrderSummaryRepository repository;
    private final ReadScopeProvider readScopeProvider;

    public OrderDashboardController(OrderSummaryRepository repository,
                                    ReadScopeProvider readScopeProvider) {
        this.repository = repository;
        this.readScopeProvider = readScopeProvider;
    }

    @GetMapping
    public PageResponse<OrderSummaryResponse> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID customerPartnerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sagaState,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredShipDateFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate requiredShipDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        DateRangeSupport.validate("requiredShipDate", requiredShipDateFrom, requiredShipDateTo);
        Page<OrderSummaryEntity> result = repository.search(
                readScopeProvider.current().tenantFilter(),
                warehouseId, customerPartnerId,
                status, sagaState, requiredShipDateFrom, requiredShipDateTo,
                PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, OrderSummaryResponse::from);
    }
}
