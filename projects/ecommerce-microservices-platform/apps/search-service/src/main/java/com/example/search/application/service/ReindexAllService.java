package com.example.search.application.service;

import com.example.search.application.port.in.IndexSyncUseCase;
import com.example.search.application.port.in.ReindexAllUseCase;
import com.example.search.application.port.out.ProductCatalogPort;
import com.example.search.domain.model.SearchDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * product-service에서 카탈로그를 읽어와 검색 인덱스에 일괄 upsert한다.
 *
 * 복구/초기화 경로 전용. 일반 경로는 Kafka 이벤트 소비로 인덱스가 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReindexAllService implements ReindexAllUseCase {

    private final ProductCatalogPort productCatalogPort;
    private final IndexSyncUseCase indexSyncUseCase;

    @Override
    public int reindexAll(int batchSize) {
        List<SearchDocument> documents = productCatalogPort.fetchAll(batchSize);
        for (SearchDocument doc : documents) {
            indexSyncUseCase.upsert(doc);
        }
        log.info("Reindex completed. total={}", documents.size());
        return documents.size();
    }
}
