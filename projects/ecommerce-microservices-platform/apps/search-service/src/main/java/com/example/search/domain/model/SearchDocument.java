package com.example.search.domain.model;

public record SearchDocument(
        String productId,
        String name,
        String description,
        long price,
        String status,
        String categoryId,
        int totalStock,
        String thumbnailUrl,
        Double score
) {
    /**
     * thumbnailUrl을 모르는 호출부용 오버로드. 기존 테스트/호출부와의 하위 호환을 위해 유지한다.
     * 신규 코드는 thumbnailUrl을 포함하는 오버로드를 사용할 것.
     */
    public static SearchDocument of(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            int totalStock
    ) {
        return new SearchDocument(productId, name, description, price, status, categoryId, totalStock, null, null);
    }

    public static SearchDocument of(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            int totalStock,
            String thumbnailUrl
    ) {
        return new SearchDocument(productId, name, description, price, status, categoryId, totalStock, thumbnailUrl, null);
    }
}
