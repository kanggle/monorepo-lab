package com.example.finance.account.application.view;

import java.util.List;

/** Paged transaction read model — keeps domain Transaction out of presentation. */
public record TransactionPageView(List<TransactionView> content,
                                  int page,
                                  int size,
                                  long totalElements,
                                  int totalPages) {
}
