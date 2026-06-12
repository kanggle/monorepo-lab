package com.example.finance.ledger.presentation.dto;

import com.example.finance.ledger.application.view.DiscrepancyView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Discrepancy response (reconciliation-api.md § 1/§ 4/§ 5). {@code expectedMinor}
 * / {@code actualMinor} are minor-units strings (F5 — never a float); {@code
 * currency} is the ISO code. The nested {@code resolution} is present only when
 * RESOLVED (omitted from JSON while OPEN). Nullable provenance fields
 * ({@code externalRef} / {@code journalEntryId}) are omitted when absent.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscrepancyResponse(
        String discrepancyId,
        String ledgerAccountCode,
        String type,
        String externalRef,
        String journalEntryId,
        String expectedMinor,
        String actualMinor,
        String currency,
        String status,
        Instant detectedAt,
        Resolution resolution) {

    /** The operator resolution record (present only when RESOLVED). */
    public record Resolution(String resolutionType, String note, String resolvedBy,
                             Instant resolvedAt) {
    }

    public static DiscrepancyResponse from(DiscrepancyView v) {
        Resolution resolution = v.resolutionType() == null ? null
                : new Resolution(v.resolutionType().name(), v.note(), v.resolvedBy(),
                v.resolvedAt());
        return new DiscrepancyResponse(
                v.discrepancyId(), v.ledgerAccountCode(), v.type().name(),
                v.externalRef(), v.journalEntryId(),
                v.expected().toMinorString(), v.actual().toMinorString(),
                v.expected().currency().code(), v.status().name(),
                v.detectedAt(), resolution);
    }
}
