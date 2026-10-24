package com.example.security.service.query.internal;

import com.example.security.service.query.SecurityMetricsQueryService;
import com.example.security.service.query.dto.SecurityMetricsDailyView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * TASK-MONO-811 — {@code GET /internal/security/metrics/daily} ({@code security-query-api.md}).
 * Behind the same {@code /internal/**} {@code InternalAuthFilter} as the other query endpoints
 * (GAP client_credentials JWT with {@code internal.invoke}, else 403 PERMISSION_DENIED).
 *
 * <p>Missing {@code tenantId} / {@code from} / {@code to} → 400 VALIDATION_ERROR
 * (MissingServletRequestParameterException, inherited handler); an unparseable instant → 400
 * VALIDATION_ERROR ({@link QueryExceptionHandler}); the service's range checks → 400 VALIDATION_ERROR.
 */
@RestController
@RequestMapping("/internal/security")
@RequiredArgsConstructor
public class SecurityMetricsQueryController {

    private final SecurityMetricsQueryService metricsQueryService;

    @GetMapping("/metrics/daily")
    public ResponseEntity<SecurityMetricsDailyView> daily(
            @RequestParam String tenantId,
            @RequestParam Instant from,
            @RequestParam Instant to) {
        return ResponseEntity.ok(metricsQueryService.daily(tenantId, from, to));
    }
}
