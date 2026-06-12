package com.example.finance.ledger.application.view;

import java.util.List;

/** A page of an account's posted lines (ledger-api.md § 2). */
public record AccountLinePageView(List<AccountLineView> content, int page, int size,
                                  long totalElements, int totalPages) {
}
