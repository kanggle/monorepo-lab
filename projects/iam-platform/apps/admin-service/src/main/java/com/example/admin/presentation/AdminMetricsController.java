package com.example.admin.presentation;

import com.example.admin.application.AdminMetricsUseCase;
import com.example.admin.application.OperatorContext;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/admin/metrics?period=week|month[&tenantId=]} (admin-api.md § GET /api/admin/metrics,
 * TASK-MONO-811). Returns the shared {@link PerformanceMetrics} shape <b>bare</b> — no envelope
 * ({@code platform/contracts/performance-metrics-contract.md}).
 *
 * <p>{@code period} is {@code required = false} on purpose (same as the SCM sibling): missing and unknown
 * take the one path — {@link MetricsPeriod#parse}'s {@link IllegalArgumentException}, which this service's
 * {@code AdminExceptionHandler} maps to {@code 400 VALIDATION_ERROR}. It is parsed after the permission
 * check, so an operator without {@code audit.read} gets 403 whatever {@code period} says.
 *
 * <p>The aspect maps this controller to {@code ActionCode.METRICS_QUERY} for its DENIED row.
 */
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsUseCase useCase;

    @GetMapping
    @RequiresPermission(Permission.AUDIT_READ)
    public ResponseEntity<PerformanceMetrics> metrics(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String tenantId) {
        MetricsPeriod parsed = MetricsPeriod.parse(period);
        OperatorContext operator = OperatorContextHolder.require();
        return ResponseEntity.ok(useCase.query(operator, parsed, tenantId));
    }
}
