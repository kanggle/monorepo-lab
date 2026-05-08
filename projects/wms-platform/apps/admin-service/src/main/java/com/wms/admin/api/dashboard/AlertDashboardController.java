package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.AlertLogResponse;
import com.wms.admin.api.dto.PageResponse;
import com.wms.admin.application.alert.AlertAcknowledgeService;
import com.wms.admin.readmodel.alert.AlertLogEntity;
import com.wms.admin.readmodel.alert.AlertLogRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 1.6}.
 *
 * <ul>
 *   <li>{@code GET /alerts} — list (VIEWER+)</li>
 *   <li>{@code POST /alerts/{alertId}/acknowledge} — acknowledge alert
 *       (OPERATOR+, the only application-layer write path on a read-model
 *       table).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/alerts")
public class AlertDashboardController {

    private static final String DEFAULT_SORT = "detectedAt,desc";
    private static final String ACTOR_HEADER = "X-Actor-Id";

    private final AlertLogRepository repository;
    private final AlertAcknowledgeService acknowledgeService;

    public AlertDashboardController(AlertLogRepository repository,
                                    AlertAcknowledgeService acknowledgeService) {
        this.repository = repository;
        this.acknowledgeService = acknowledgeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('WMS_VIEWER')")
    public PageResponse<AlertLogResponse> list(
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Boolean acknowledged,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedAtFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant detectedAtTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort) {
        DateRangeSupport.validate("detectedAt", detectedAtFrom, detectedAtTo);
        Page<AlertLogEntity> result = repository.search(alertType, warehouseId, acknowledged,
                detectedAtFrom, detectedAtTo, PageableSupport.pageable(page, size, sort));
        return PageResponse.from(result, sort, AlertLogResponse::from);
    }

    @PostMapping("/{alertId}/acknowledge")
    @PreAuthorize("hasRole('WMS_OPERATOR')")
    public ResponseEntity<AlertLogResponse> acknowledge(
            @PathVariable UUID alertId,
            @RequestHeader(value = ACTOR_HEADER, required = false) String actorId) {
        AlertLogEntity row = acknowledgeService.acknowledge(alertId, actorId);
        return ResponseEntity.ok(AlertLogResponse.from(row));
    }
}
