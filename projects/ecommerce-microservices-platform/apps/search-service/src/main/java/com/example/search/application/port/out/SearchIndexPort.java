package com.example.search.application.port.out;

import com.example.search.domain.model.SearchDocument;

import java.util.Optional;

public interface SearchIndexPort {

    void upsert(SearchDocument document);

    void delete(String productId);

    void updateStock(String productId, int totalStock, String status);

    Optional<SearchDocument> findById(String productId);

    void updateThumbnailUrl(String productId, String thumbnailUrl);
}
