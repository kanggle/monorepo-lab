package com.example.batch.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * HTTP client for search-service (TASK-BE-409 / AC-2).
 *
 * <p>Consumes {@code GET /api/search/products?q={name}} — public, no authentication required
 * (see {@code specs/contracts/http/search-api.md}). Used by
 * {@link com.example.batch.application.SearchIndexConsistencyJob} to spot-check whether a
 * given ON_SALE product name appears in the Elasticsearch-backed search results.
 *
 * <p>Mirrors review-service {@code OrderServiceClient} (RestClient, {@code @Value} base-url).
 * HTTP failures propagate as unchecked exceptions and are caught by the job to record a
 * FAILED history entry — they do NOT propagate further (AC-2 isolation rule).
 */
@Slf4j
@Component
public class SearchServiceClient {

    private final RestClient restClient;

    public SearchServiceClient(@Value("${search-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Search products by name keyword.
     *
     * @param name the product name to search for (maps to {@code ?q=} parameter)
     * @return search response; never null
     */
    public SearchResponse searchByName(String name) {
        log.debug("Searching for product name={}", name);
        SearchResponse response = restClient.get()
                .uri("/api/search/products?q={q}", name)
                .retrieve()
                .body(SearchResponse.class);
        return response != null ? response : new SearchResponse(name, List.of(), 0, 20, 0L);
    }

    /**
     * Response shape for {@code GET /api/search/products} (search-api.md).
     *
     * @param query         the echoed search query
     * @param content       list of matching product hits
     * @param page          current page
     * @param size          page size
     * @param totalElements total hits in the index
     */
    public record SearchResponse(
            String query,
            List<SearchHit> content,
            int page,
            int size,
            long totalElements) {
    }

    /**
     * A single product hit returned by the search service.
     *
     * @param productId UUID of the product
     * @param name      product name
     */
    public record SearchHit(
            UUID productId,
            String name,
            long price,
            String status,
            String thumbnailUrl,
            UUID categoryId,
            double score) {
    }
}
