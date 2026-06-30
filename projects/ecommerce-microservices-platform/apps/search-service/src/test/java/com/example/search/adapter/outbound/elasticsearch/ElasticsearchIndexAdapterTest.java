package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import com.example.search.NoriElasticsearchContainer;
import com.example.search.domain.model.SearchDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
@DisplayName("ElasticsearchIndexAdapter 통합 테스트")
class ElasticsearchIndexAdapterTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", NoriElasticsearchContainer::httpUri);
    }

    @Autowired
    private ElasticsearchIndexAdapter adapter;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private IndexProperties indexProperties;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 간 데이터 격리: 인덱스를 초기화
        try {
            elasticsearchClient.deleteByQuery(d -> d
                    .index(indexProperties.name())
                    .query(q -> q.matchAll(m -> m))
            );
            elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("upsert 성공 - 문서가 인덱스에 저장된다")
    void upsert_validDocument_storedInIndex() throws Exception {
        String productId = "test-product-" + System.nanoTime();
        SearchDocument document = SearchDocument.of(productId, "노트북", "좋은 노트북", 1000000L, "ON_SALE", "cat1", 10);

        adapter.upsert(document);

        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));
        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.found()).isTrue();
        assertThat(response.source()).containsEntry("name", "노트북");
    }

    @Test
    @DisplayName("delete 성공 - 문서가 인덱스에서 삭제된다")
    void delete_existingDocument_removedFromIndex() throws Exception {
        String productId = "del-product-" + System.nanoTime();
        SearchDocument document = SearchDocument.of(productId, "삭제상품", "설명", 5000L, "ON_SALE", "cat1", 1);
        adapter.upsert(document);
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        adapter.delete(productId);

        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));
        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.found()).isFalse();
    }

    @Test
    @DisplayName("delete - 존재하지 않는 문서 삭제 시 예외가 발생하지 않는다")
    void delete_nonExistingDocument_noException() {
        adapter.delete("non-existing-product-id");
    }

    @Test
    @DisplayName("updateStock - 재고와 상태가 갱신된다")
    void updateStock_existingDocument_stockAndStatusUpdated() throws Exception {
        String productId = "stock-product-" + System.nanoTime();
        SearchDocument document = SearchDocument.of(productId, "재고상품", "설명", 10000L, "ON_SALE", "cat1", 10);
        adapter.upsert(document);
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        adapter.updateStock(productId, 0, "SOLD_OUT");

        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));
        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.found()).isTrue();
        assertThat(response.source()).containsEntry("status", "SOLD_OUT");
        assertThat(response.source()).containsEntry("totalStock", 0);
    }

    @Test
    @DisplayName("upsert 중복 호출 시 덮어쓰기(멱등성)")
    void upsert_twice_latestDataPersisted() throws Exception {
        String productId = "idem-product-" + System.nanoTime();
        SearchDocument first = SearchDocument.of(productId, "첫번째", "설명", 1000L, "ON_SALE", "cat1", 5);
        SearchDocument second = SearchDocument.of(productId, "두번째", "설명2", 2000L, "ON_SALE", "cat1", 10);

        adapter.upsert(first);
        adapter.upsert(second);
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.source()).containsEntry("name", "두번째");
    }

    @Test
    @DisplayName("findById - 존재하는 문서 조회 시 Optional에 값이 반환된다")
    void findById_existingDocument_returnsPresent() {
        String productId = "find-product-" + System.nanoTime();
        SearchDocument document = SearchDocument.of(productId, "조회상품", "설명", 25000L, "ON_SALE", "cat1", 7);
        adapter.upsert(document);
        try {
            elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));
        } catch (Exception ignored) {}

        var result = adapter.findById(productId);

        assertThat(result).isPresent();
        assertThat(result.get().productId()).isEqualTo(productId);
        assertThat(result.get().name()).isEqualTo("조회상품");
        assertThat(result.get().totalStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("findById - 존재하지 않는 문서 조회 시 Optional.empty가 반환된다")
    void findById_nonExistingDocument_returnsEmpty() {
        var result = adapter.findById("non-existing-" + System.nanoTime());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateStock - docAsUpsert로 존재하지 않는 문서도 부분 생성된다")
    void updateStock_nonExistingDocument_createsPartialDocument() throws Exception {
        String productId = "upsert-stock-" + System.nanoTime();

        adapter.updateStock(productId, 5, "ON_SALE");

        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));
        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.found()).isTrue();
        assertThat(response.source()).containsEntry("totalStock", 5);
        assertThat(response.source()).containsEntry("status", "ON_SALE");
    }
}
