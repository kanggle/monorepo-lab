package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;
import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;
import com.example.search.domain.model.SearchSort;
import com.example.search.application.port.out.SearchQueryPort;
import com.example.search.application.exception.SearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchQueryAdapter implements SearchQueryPort {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexProperties indexProperties;

    @Override
    public SearchProductResult search(SearchProductQuery query) {
        try {
            int from = query.page() * query.size();

            SearchRequest request = SearchRequest.of(s -> s
                    .index(indexProperties.name())
                    .query(buildQuery(query))
                    .from(from)
                    .size(query.size())
                    .sort(buildSort(query.sort()))
                    .aggregations("categories", a -> a
                            .terms(t -> t.field("categoryId").size(20))
                    )
                    .aggregations("price_ranges", a -> a
                            .range(r -> r.field("price")
                                    .ranges(rr -> rr.to(10000.0))
                                    .ranges(rr -> rr.from(10000.0).to(50000.0))
                                    .ranges(rr -> rr.from(50000.0).to(100000.0))
                                    .ranges(rr -> rr.from(100000.0))
                            )
                    )
            );

            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            return toResult(response);
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            log.error("Elasticsearch search failed", e);
            throw new SearchException("Search failed", e);
        }
    }

    private Query buildQuery(SearchProductQuery query) {
        var filter = query.filter();
        return Query.of(q -> q.bool(builder -> {
            builder.must(m -> m.multiMatch(mm -> mm
                    .query(filter.keyword())
                    .fields("name", "description")
            ));
            builder.filter(f -> f.term(t -> t.field("status").value(filter.status())));
            if (filter.categoryId() != null) {
                builder.filter(f -> f.term(t -> t.field("categoryId").value(filter.categoryId())));
            }
            if (filter.minPrice() != null || filter.maxPrice() != null) {
                builder.filter(f -> f.range(r -> r.number(n -> {
                    var priceRange = n.field("price");
                    if (filter.minPrice() != null) priceRange = priceRange.gte(filter.minPrice().doubleValue());
                    if (filter.maxPrice() != null) priceRange = priceRange.lte(filter.maxPrice().doubleValue());
                    return priceRange;
                })));
            }
            return builder;
        }));
    }

    private SortOptions buildSort(SearchSort sort) {
        return switch (sort) {
            case PRICE_ASC -> SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
            case PRICE_DESC -> SortOptions.of(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
            case NEWEST -> SortOptions.of(s -> s.field(f -> f.field("_id").order(SortOrder.Desc)));
            case RELEVANCE -> SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc)));
        };
    }

    @SuppressWarnings("unchecked")
    private SearchProductResult toResult(SearchResponse<Map> response) {
        HitsMetadata<Map> hits = response.hits();
        if (hits == null) {
            return new SearchProductResult(Collections.emptyList(), new FacetResult(Collections.emptyList(), Collections.emptyList()), 0L);
        }

        List<SearchDocument> documents = new ArrayList<>();
        for (Hit<Map> hit : hits.hits()) {
            Map<String, Object> source = hit.source();
            if (source == null) continue;

            documents.add(ElasticsearchFieldMapper.toSearchDocument(
                    source, hit.score() != null ? hit.score().doubleValue() : null
            ));
        }

        long totalElements = hits.total() != null ? hits.total().value() : 0L;
        FacetResult facets = extractFacets(response);

        return new SearchProductResult(documents, facets, totalElements);
    }

    private FacetResult extractFacets(SearchResponse<Map> response) {
        List<FacetResult.CategoryFacet> categories = new ArrayList<>();
        List<FacetResult.PriceRangeFacet> priceRanges = new ArrayList<>();

        var aggs = response.aggregations();
        if (aggs != null) {
            var categoriesAgg = aggs.get("categories");
            if (categoriesAgg != null && categoriesAgg.isSterms()) {
                for (StringTermsBucket bucket : categoriesAgg.sterms().buckets().array()) {
                    categories.add(new FacetResult.CategoryFacet(
                            bucket.key().stringValue(),
                            bucket.docCount()
                    ));
                }
            }

            var priceRangesAgg = aggs.get("price_ranges");
            if (priceRangesAgg != null && priceRangesAgg.isRange()) {
                for (var bucket : priceRangesAgg.range().buckets().array()) {
                    Long min = bucket.from() != null ? (long) bucket.from().doubleValue() : null;
                    Long max = bucket.to() != null ? (long) bucket.to().doubleValue() : null;
                    priceRanges.add(new FacetResult.PriceRangeFacet(min, max, bucket.docCount()));
                }
            }
        }

        return new FacetResult(categories, priceRanges);
    }
}
