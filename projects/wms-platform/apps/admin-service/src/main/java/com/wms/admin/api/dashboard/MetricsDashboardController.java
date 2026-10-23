package com.wms.admin.api.dashboard;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.wms.admin.application.dashboard.WarehouseMetricsService;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 1.2} — performance metrics for the console overview
 * strip (ADR-MONO-084 P3, TASK-MONO-808). Warehouse-global like throughput.
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard/metrics")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class MetricsDashboardController {

    private final WarehouseMetricsService metricsService;

    public MetricsDashboardController(WarehouseMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * {@code period} is read as optional and parsed here so a missing value and an
     * unknown one fail the same way — {@link MetricsPeriod#parse} throws
     * {@link IllegalArgumentException}, which the advice maps to 400 {@code VALIDATION_ERROR}.
     */
    @GetMapping
    public PerformanceMetrics get(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) UUID warehouseId) {
        return metricsService.getMetrics(MetricsPeriod.parse(period), warehouseId);
    }
}
