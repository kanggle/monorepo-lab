package com.example.batch.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
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
        // WARNING fix: explicit timeouts prevent a hung search-service from blocking the
        // scheduler thread for the entire ShedLock window. 5s connect / 10s read mirrors
        // the values used in ProductServiceClient for consistency.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
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
     * <p>B-3 fix: {@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures that extra fields
     * present in the actual search-api response (e.g. {@code facets}) do not cause deserialization
     * failures under a strict Jackson config ({@code FAIL_ON_UNKNOWN_PROPERTIES=true}).
     *
     * @param query         the echoed search query
     * @param content       list of matching product hits
     * @param page          current page
     * @param size          page size
     * @param totalElements total hits in the index
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
     * <p>B-3 fix: {@code @JsonIgnoreProperties(ignoreUnknown = true)} for symmetry with
     * {@link SearchResponse} — guards against future search-api additions to individual hit
     * objects (e.g. highlight fragments, sort values).
     *
     * @param productId UUID of the product
     * @param name      product name
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
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
