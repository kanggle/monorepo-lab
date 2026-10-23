package com.example.scmplatform.procurement.presentation.controller;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderMetricsService;
import com.example.scmplatform.procurement.presentation.advice.InvalidQueryParameterException;
import com.example.security.servlet.actor.ActorContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overview performance metrics ({@code procurement-api.md} § GET /api/procurement/po/metrics,
 * TASK-MONO-810). Returns the shared {@link PerformanceMetrics} shape bare — no
 * {@code {data, meta}} envelope ({@code platform/contracts/performance-metrics-contract.md}).
 *
 * <p>The literal {@code /po/metrics} pattern is more specific than
 * {@code PurchaseOrderController}'s {@code /po/{poId}}, so Spring MVC routes it here; the slice
 * test loads both controllers to prove it.
 *
 * <p>{@code period} is {@code required = false} on purpose: missing and unknown then take the one
 * path — {@link MetricsPeriod#parse}'s {@link IllegalArgumentException}, re-thrown as
 * {@link InvalidQueryParameterException} so it answers 400 (this service maps a bare
 * {@code IllegalArgumentException} to 422).
 */
@RestController
@RequestMapping("/api/procurement/po/metrics")
@RequiredArgsConstructor
public class PurchaseOrderMetricsController {

    private final PurchaseOrderMetricsService service;

    @GetMapping
    public ResponseEntity<PerformanceMetrics> metrics(@RequestParam(required = false) String period) {
        MetricsPeriod parsed;
        try {
            parsed = MetricsPeriod.parse(period);
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryParameterException(e.getMessage(), e);
        }
        ActorContext actor = ActorContextResolver.currentOrThrow(ActorContext.class);
        return ResponseEntity.ok(service.getMetrics(actor, parsed));
    }
}
