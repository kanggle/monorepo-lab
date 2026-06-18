package com.example.search.application.service;

import com.example.search.application.port.in.IndexSyncUseCase;
import com.example.search.domain.model.ProductStatus;
import com.example.search.domain.model.SearchDocument;
import com.example.search.application.port.out.SearchIndexPort;
import com.example.search.application.port.out.SearchMetricsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSyncService implements IndexSyncUseCase {

    private final SearchIndexPort searchIndexPort;
    private final SearchMetricsPort searchMetrics;

    public void upsert(SearchDocument document) {
        log.info("Upserting search index for productId={}", document.productId());
        executeWithMetrics("created", () -> searchIndexPort.upsert(document));
    }

    public void delete(String productId) {
        log.info("Deleting search index for productId={}", productId);
        executeWithMetrics("deleted", () -> searchIndexPort.delete(productId));
    }

    public void upsertPreservingStock(SearchDocument document) {
        log.info("Upserting search index (preserving stock) for productId={}", document.productId());
        executeWithMetrics("updated", () -> {
            int existingStock = 0;
            try {
                Optional<SearchDocument> existing = searchIndexPort.findById(document.productId());
                existingStock = existing.map(SearchDocument::totalStock).orElse(0);
            } catch (Exception e) {
                log.warn("Failed to retrieve existing stock for productId={}, falling back to 0", document.productId(), e);
            }

            SearchDocument withStock = SearchDocument.of(
                    document.productId(),
                    document.name(),
                    document.description(),
                    document.price(),
                    document.status(),
                    document.categoryId(),
                    existingStock,
                    document.thumbnailUrl(),
                    document.tenantId()
            );
            searchIndexPort.upsert(withStock);
        });
    }

    public void updateThumbnailUrl(String productId, String thumbnailUrl) {
        log.info("Updating thumbnailUrl for productId={}", productId);
        executeWithMetrics("images-updated", () -> searchIndexPort.updateThumbnailUrl(productId, thumbnailUrl));
    }

    public void updateStock(String productId, int currentStock) {
        String status = currentStock == 0 ? ProductStatus.SOLD_OUT.name() : ProductStatus.ON_SALE.name();
        log.info("Updating stock for productId={}, currentStock={}, status={}", productId, currentStock, status);
        executeWithMetrics("updated", () -> searchIndexPort.updateStock(productId, currentStock, status));
    }

    private void executeWithMetrics(String eventType, Runnable operation) {
        try {
            operation.run();
            searchMetrics.incrementIndexSync(eventType);
        } catch (Exception e) {
            searchMetrics.incrementIndexSyncFailure();
            throw e;
        }
    }
}
