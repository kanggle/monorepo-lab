package com.example.finance.account.presentation.controller;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.TransactionMetricsService;
import com.example.finance.account.presentation.advice.InvalidQueryParameterException;
import com.example.security.servlet.actor.ActorContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Overview performance metrics ({@code account-api.md} § GET /api/finance/accounts/metrics,
 * TASK-MONO-812). Returns the shared {@link PerformanceMetrics} shape bare — no
 * {@code {data, meta}} envelope ({@code platform/contracts/performance-metrics-contract.md}).
 *
 * <p>Tenant-wide totals only — never a per-account row ({@code account-api.md} keeps the account
 * set unlisted). The path sits under {@code /accounts} because that is the prefix the finance
 * gateway routes to this service ({@code gateway-service} {@code Path=/api/finance/accounts/**});
 * the literal {@code /accounts/metrics} is more specific than {@code AccountController}'s
 * {@code /accounts/{id}}, so Spring MVC routes it here — the slice test loads both to prove it.
 *
 * <p>Authorization is the URL gate's read authorities ({@code SecurityConfig}: {@code GET
 * /api/finance/**}), the same as every other account read.
 *
 * <p>{@code period} is {@code required = false} on purpose: missing and unknown then take the one
 * path — {@link MetricsPeriod#parse}'s {@link IllegalArgumentException}, re-thrown as
 * {@link InvalidQueryParameterException} so it answers 400 (this service maps a bare
 * {@code IllegalArgumentException} to 422).
 */
@RestController
@RequestMapping("/api/finance/accounts/metrics")
@RequiredArgsConstructor
public class TransactionMetricsController {

    private final TransactionMetricsService service;

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
