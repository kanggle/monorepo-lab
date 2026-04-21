package com.example.search.application.port.out;

import com.example.search.domain.model.SearchDocument;

import java.util.List;

/**
 * 외부 product-service에서 카탈로그 데이터를 읽어오기 위한 포트.
 *
 * 이벤트(ProductCreated 등) 유실 또는 초기 시드 데이터(Flyway로 DB에 삽입되어
 * 이벤트가 발행되지 않은 경우) 복구를 위한 reindex 경로에서 사용된다.
 * 일반 경로에서는 Kafka 이벤트 소비가 인덱스를 최신으로 유지한다.
 */
public interface ProductCatalogPort {

    /**
     * 모든 상품을 {@link SearchDocument}로 변환해 반환한다. 내부적으로 페이지네이션해 순회한다.
     *
     * @param batchSize 배치당 상품 수 (product-service 페이지 size)
     */
    List<SearchDocument> fetchAll(int batchSize);
}
