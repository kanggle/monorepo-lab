package com.example.search.application.port.in;

public interface ReindexAllUseCase {

    /**
     * product-service의 모든 상품을 Elasticsearch 인덱스에 upsert한다.
     *
     * 이벤트(ProductCreated 등) 유실 또는 초기 시드 데이터(Flyway로 DB에만 삽입되어
     * 이벤트가 발행되지 않은 경우) 복구 경로로 사용한다.
     *
     * @param batchSize product-service 리스트 조회의 페이지 size
     * @return 인덱싱된 상품 수
     */
    int reindexAll(int batchSize);
}
