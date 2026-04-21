package com.example.search.adapter.inbound.web.dto;

import com.example.search.application.dto.SearchProductResult;
import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;

import java.util.List;

public record SearchProductResponse(
        String query,
        List<ProductItem> content,
        FacetsDto facets,
        int page,
        int size,
        long totalElements
) {

    public record ProductItem(
            String productId,
            String name,
            long price,
            String status,
            String thumbnailUrl,
            String categoryId,
            Double score
    ) {
        public static ProductItem from(SearchDocument doc) {
            return new ProductItem(
                    doc.productId(),
                    doc.name(),
                    doc.price(),
                    doc.status(),
                    doc.thumbnailUrl(),
                    doc.categoryId(),
                    doc.score()
            );
        }
    }

    public record FacetsDto(
            List<CategoryFacetDto> categories,
            List<PriceRangeFacetDto> priceRanges
    ) {
        public record CategoryFacetDto(String id, String name, long count) {}
        public record PriceRangeFacetDto(Long min, Long max, long count) {}

        public static FacetsDto from(FacetResult facets) {
            List<CategoryFacetDto> categories = facets.categories().stream()
                    .map(c -> new CategoryFacetDto(c.id(), null, c.count()))
                    .toList();
            List<PriceRangeFacetDto> priceRanges = facets.priceRanges().stream()
                    .map(r -> new PriceRangeFacetDto(r.min(), r.max(), r.count()))
                    .toList();
            return new FacetsDto(categories, priceRanges);
        }
    }

    public static SearchProductResponse from(String query, SearchProductResult result, int page, int size) {
        return new SearchProductResponse(
                query,
                result.content().stream().map(ProductItem::from).toList(),
                FacetsDto.from(result.facets()),
                page,
                size,
                result.totalElements()
        );
    }
}
