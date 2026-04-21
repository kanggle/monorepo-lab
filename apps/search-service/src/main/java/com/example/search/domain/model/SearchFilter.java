package com.example.search.domain.model;

public record SearchFilter(
        String keyword,
        String categoryId,
        Long minPrice,
        Long maxPrice,
        String status
) {

    public SearchFilter {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        if (minPrice != null && minPrice < 0) {
            throw new IllegalArgumentException("minPrice must be >= 0");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new IllegalArgumentException("maxPrice must be >= 0");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");
        }
        keyword = keyword.trim();
        status = (status == null || status.isBlank()) ? ProductStatus.ON_SALE.name() : status;
    }

    public static SearchFilter of(String keyword, String categoryId, Long minPrice, Long maxPrice, String status) {
        return new SearchFilter(keyword, categoryId, minPrice, maxPrice, status);
    }
}
