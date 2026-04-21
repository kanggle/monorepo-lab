package com.example.search.domain.model;

import java.util.List;

public record FacetResult(
        List<CategoryFacet> categories,
        List<PriceRangeFacet> priceRanges
) {
    public record CategoryFacet(String id, long count) {}

    public record PriceRangeFacet(Long min, Long max, long count) {}
}
