package com.example.batch.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

/**
 * HTTP client for product-service (TASK-BE-409 / AC-2).
 *
 * <p>Consumes {@code GET /api/products?page=&size=&status=ON_SALE} — public, no authentication
 * required (see {@code specs/contracts/http/product-api.md}). Used by
 * {@link com.example.batch.application.SearchIndexConsistencyJob} to page through the authority
 * product catalog for the search-index spot-check.
 *
 * <p>Mirrors review-service {@code OrderServiceClient} (RestClient, {@code @Value} base-url).
 * HTTP failures propagate as unchecked exceptions and are caught by the job to record a
 * FAILED history entry — they do NOT propagate further (AC-2 isolation rule).
 */
@Slf4j
@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(@Value("${product-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * List ON_SALE products with pagination.
     *
     * @param page zero-based page index
     * @param size page size (max 100 per product-api.md)
     * @return paged response; never null
     */
    public ProductPageResponse listOnSale(int page, int size) {
        log.debug("Fetching ON_SALE products page={} size={}", page, size);
        ProductPageResponse response = restClient.get()
                .uri("/api/products?page={page}&size={size}&status=ON_SALE", page, size)
                .retrieve()
                .body(ProductPageResponse.class);
        return response != null ? response : new ProductPageResponse(List.of(), page, size, 0);
    }

    /**
     * Response shape for {@code GET /api/products} (product-api.md).
     *
     * @param content list of product summaries
     * @param page    current page
     * @param size    page size
     * @param totalElements total products matching the filter
     */
    public record ProductPageResponse(
            List<ProductSummary> content,
            int page,
            int size,
            long totalElements) {
    }

    /**
     * A single product entry in the paginated catalog list.
     *
     * @param id   product UUID
     * @param name product name (used as search query term in the spot-check)
     * @param status product status (always ON_SALE for this client's use case)
     */
    public record ProductSummary(
            UUID id,
            String name,
            String status,
            long price,
            String thumbnailUrl,
            UUID categoryId,
            String sellerId) {
    }
}
