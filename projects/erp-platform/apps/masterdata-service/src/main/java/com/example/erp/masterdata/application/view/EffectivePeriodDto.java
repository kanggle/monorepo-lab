package com.example.erp.masterdata.application.view;

import java.time.LocalDate;

/**
 * Presentation view of the effective-dating period
 * (masterdata-api.md § Common shapes — {@code EffectivePeriod}).
 *
 * <p>Serialises as the nested {@code { "effectiveFrom": "<DATE>",
 * "effectiveTo": "<DATE>|null" }} the contract (and the platform-console
 * consumer) require — NOT the flat top-level fields the {@code *View} records
 * previously emitted (TASK-ERP-BE-006 contract-conformance fix). {@code
 * effectiveTo} is {@code null} for an open-ended / currently-active row.
 */
public record EffectivePeriodDto(LocalDate effectiveFrom, LocalDate effectiveTo) {
}
