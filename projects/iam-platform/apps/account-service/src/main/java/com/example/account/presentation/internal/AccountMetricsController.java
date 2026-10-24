package com.example.account.presentation.internal;

import com.example.account.application.result.AccountMetricsDailyResult;
import com.example.account.application.service.AccountMetricsQueryService;
import com.example.account.presentation.dto.response.AccountMetricsDailyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * TASK-MONO-811 — {@code GET /internal/account-metrics/daily} ({@code admin-to-account.md}).
 * Behind the {@code /internal/**} chain ({@code SecurityConfig}: GAP client_credentials JWT carrying
 * {@code internal.invoke}, else 401 UNAUTHORIZED). Its own prefix — not under
 * {@code /internal/accounts/} — so it can never be read as an {@code {accountId}}.
 *
 * <p>{@code from} / {@code to} are read as strings and parsed here: this service has no handler for a
 * binding type mismatch (it would answer 500), so a bad instant becomes the
 * {@link IllegalArgumentException} the shared handler maps to 400 VALIDATION_ERROR.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/account-metrics")
public class AccountMetricsController {

    private final AccountMetricsQueryService metricsQueryService;

    @GetMapping("/daily")
    public ResponseEntity<AccountMetricsDailyResponse> daily(
            @RequestParam String tenantId,
            @RequestParam String from,
            @RequestParam String to) {
        Instant fromInstant = parseInstant("from", from);
        Instant toInstant = parseInstant("to", to);
        AccountMetricsDailyResult result = metricsQueryService.daily(tenantId, fromInstant, toInstant);
        return ResponseEntity.ok(AccountMetricsDailyResponse.of(tenantId, fromInstant, toInstant, result));
    }

    /** ISO-8601 instant ({@code …Z}) or offset date-time ({@code …+09:00}). */
    static Instant parseInstant(String name, String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException notOffset) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(name + " must be an ISO-8601 instant");
            }
        }
    }
}
