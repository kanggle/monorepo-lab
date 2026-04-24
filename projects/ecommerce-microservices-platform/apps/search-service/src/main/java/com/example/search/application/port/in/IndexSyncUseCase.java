package com.example.search.application.port.in;

import com.example.search.domain.model.SearchDocument;

public interface IndexSyncUseCase {

    void upsert(SearchDocument document);

    void delete(String productId);

    void upsertPreservingStock(SearchDocument document);

    void updateStock(String productId, int currentStock);

    void updateThumbnailUrl(String productId, String thumbnailUrl);
}
