package com.example.erp.approval.presentation.controller;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.ApprovalMetricsService;
import com.example.security.servlet.actor.ActorContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overview performance metrics (approval-api.md § GET /api/erp/approval/metrics,
 * TASK-MONO-809). Returns the shared {@link PerformanceMetrics} shape bare (no
 * {@code {data, meta}} envelope — {@code platform/contracts/performance-metrics-contract.md}).
 *
 * <p>{@code period} is declared {@code required = false} on purpose: Spring's own
 * missing-parameter path and {@link MetricsPeriod#parse}'s {@link IllegalArgumentException}
 * then both answer {@code 400 VALIDATION_ERROR} through the one handler arm.
 */
@RestController
@RequestMapping("/api/erp/approval/metrics")
@RequiredArgsConstructor
public class ApprovalMetricsController {

    private final ApprovalMetricsService service;

    @GetMapping
    public ResponseEntity<PerformanceMetrics> metrics(
            @RequestParam(required = false) String period) {
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return ResponseEntity.ok(service.getMetrics(actor, MetricsPeriod.parse(period)));
    }
}
