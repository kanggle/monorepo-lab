package com.example.finance.account.presentation.dto;

import java.util.List;

/** Paged list payload — page meta inlined per account-api.md transactions. */
public record PageResponse<T>(List<T> content,
                              int page,
                              int size,
                              long totalElements,
                              int totalPages) {
}
