package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import com.example.search.domain.model.SearchDocument;
import com.example.search.application.port.out.SearchIndexPort;
import com.example.search.application.exception.SearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexAdapter implements SearchIndexPort {

    private final ElasticsearchClient elasticsearchClient;
    private final IndexProperties indexProperties;

    @Override
    public void upsert(SearchDocument document) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("productId", document.productId());
            doc.put("name", document.name() != null ? document.name() : "");
            doc.put("description", document.description() != null ? document.description() : "");
            doc.put("price", document.price());
            doc.put("status", document.status() != null ? document.status() : "");
            doc.put("categoryId", document.categoryId() != null ? document.categoryId() : "");
            doc.put("totalStock", document.totalStock());
            if (document.thumbnailUrl() != null) {
                doc.put("thumbnailUrl", document.thumbnailUrl());
            }
            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(indexProperties.name())
                    .id(document.productId())
                    .document(doc)
            ));
        } catch (Exception e) {
            log.error("Failed to upsert document for productId={}", document.productId(), e);
            throw new SearchException("Failed to upsert search index for productId=" + document.productId(), e);
        }
    }

    @Override
    public void delete(String productId) {
        try {
            elasticsearchClient.delete(DeleteRequest.of(d -> d
                    .index(indexProperties.name())
                    .id(productId)
            ));
        } catch (Exception e) {
            log.warn("Failed to delete document for productId={} (may not exist)", productId, e);
        }
    }

    @Override
    public void updateStock(String productId, int totalStock, String status) {
        try {
            Map<String, Object> partial = Map.of(
                    "totalStock", totalStock,
                    "status", status
            );
            elasticsearchClient.update(UpdateRequest.of(u -> u
                    .index(indexProperties.name())
                    .id(productId)
                    .docAsUpsert(true)
                    .doc(partial)
            ), Map.class);
        } catch (Exception e) {
            log.error("Failed to update stock for productId={}", productId, e);
            throw new SearchException("Failed to update stock for productId=" + productId, e);
        }
    }

    @Override
    public void updateThumbnailUrl(String productId, String thumbnailUrl) {
        try {
            Map<String, Object> partial = new java.util.HashMap<>();
            partial.put("thumbnailUrl", thumbnailUrl);
            elasticsearchClient.update(UpdateRequest.of(u -> u
                    .index(indexProperties.name())
                    .id(productId)
                    .docAsUpsert(false)
                    .doc(partial)
            ), Map.class);
        } catch (Exception e) {
            log.error("Failed to update thumbnailUrl for productId={}", productId, e);
            throw new SearchException("Failed to update thumbnailUrl for productId=" + productId, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<SearchDocument> findById(String productId) {
        try {
            GetResponse<Map> response = elasticsearchClient.get(g -> g
                    .index(indexProperties.name())
                    .id(productId), Map.class);

            if (!response.found() || response.source() == null) {
                return Optional.empty();
            }

            Map<String, Object> source = response.source();
            return Optional.of(ElasticsearchFieldMapper.toSearchDocument(source, null));
        } catch (Exception e) {
            log.warn("Failed to find document for productId={}", productId, e);
            return Optional.empty();
        }
    }
}
