package com.example.product.application.dto;

import java.util.List;

public record ProductListResult(
        List<ProductSummary> content,
        int page,
        int size,
        long totalElements
) {}
