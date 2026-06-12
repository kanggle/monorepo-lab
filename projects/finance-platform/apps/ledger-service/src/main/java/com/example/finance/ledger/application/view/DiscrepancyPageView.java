package com.example.finance.ledger.application.view;

import java.util.List;

/**
 * A page of the discrepancy review queue (reconciliation-api.md § 4). Carries the
 * page content + pagination meta.
 */
public record DiscrepancyPageView(List<DiscrepancyView> content, int page, int size,
                                  long totalElements, int totalPages) {
}
