package com.example.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import com.example.search.adapter.inbound.event.ProductCreatedEvent;
import com.example.search.adapter.inbound.event.ProductCreatedConsumer;
import com.example.search.adapter.inbound.event.ProductDeletedConsumer;
import com.example.search.adapter.inbound.event.ProductDeletedEvent;
import com.example.search.adapter.inbound.event.ProductUpdatedConsumer;
import com.example.search.adapter.inbound.event.ProductUpdatedEvent;
import com.example.search.adapter.inbound.event.StockChangedConsumer;
import com.example.search.adapter.inbound.event.StockChangedEvent;
import com.example.search.adapter.outbound.elasticsearch.IndexProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("인덱스 동기화 통합 테스트")
class IndexSyncIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.11.1")
                            .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch")
            )
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private ProductCreatedConsumer productCreatedConsumer;

    @Autowired
    private ProductUpdatedConsumer productUpdatedConsumer;

    @Autowired
    private ProductDeletedConsumer productDeletedConsumer;

    @Autowired
    private StockChangedConsumer stockChangedConsumer;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private IndexProperties indexProperties;

    private ProductCreatedEvent createdEvent(String productId, String name, List<ProductCreatedEvent.VariantPayload> variants) {
        return new ProductCreatedEvent(
                "evt-id", "ProductCreated", "2026-03-23T00:00:00Z", "product-service",
                new ProductCreatedEvent.ProductCreatedPayload(productId, name, "설명", 1500000L, "ON_SALE", "electronics", null, variants)
        );
    }

    @Test
    @DisplayName("ProductCreated 이벤트 발행 시 인덱스에 문서가 추가된다")
    void productCreated_event_indexedInElasticsearch() throws Exception {
        String productId = "integ-create-" + System.nanoTime();

        productCreatedConsumer.handle(createdEvent(productId, "노트북",
                List.of(new ProductCreatedEvent.VariantPayload("v1", "블랙", 5, 0))));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.found()).isTrue();
        assertThat(response.source()).containsEntry("name", "노트북");
        assertThat(response.source()).containsEntry("totalStock", 5);
    }

    @Test
    @DisplayName("ProductUpdated 이벤트 발행 시 인덱스 문서가 갱신된다")
    void productUpdated_event_documentUpdatedInElasticsearch() throws Exception {
        String productId = "integ-update-" + System.nanoTime();
        productCreatedConsumer.handle(createdEvent(productId, "원래상품", List.of()));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        productUpdatedConsumer.handle(new ProductUpdatedEvent(
                "evt-id", "ProductUpdated", "2026-03-23T00:00:00Z", "product-service",
                new ProductUpdatedEvent.ProductUpdatedPayload(productId, "수정된상품", "새설명", 2000L, "ON_SALE", "cat2", null)
        ));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.source()).containsEntry("name", "수정된상품");
        assertThat(response.source()).containsEntry("categoryId", "cat2");
    }

    @Test
    @DisplayName("ProductDeleted 이벤트 발행 시 인덱스에서 문서가 삭제된다")
    void productDeleted_event_documentRemovedFromElasticsearch() throws Exception {
        String productId = "integ-delete-" + System.nanoTime();
        productCreatedConsumer.handle(createdEvent(productId, "삭제될상품", List.of()));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        productDeletedConsumer.handle(new ProductDeletedEvent(
                "evt-id", "ProductDeleted", "2026-03-23T00:00:00Z", "product-service",
                new ProductDeletedEvent.ProductDeletedPayload(productId)
        ));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.found()).isFalse();
    }

    @Test
    @DisplayName("StockChanged 이벤트 발행 시 재고가 0이면 SOLD_OUT으로 갱신된다")
    void stockChanged_zeroStock_soldOutInElasticsearch() throws Exception {
        String productId = "integ-stock-" + System.nanoTime();
        productCreatedConsumer.handle(createdEvent(productId, "재고상품",
                List.of(new ProductCreatedEvent.VariantPayload("v1", "옵션1", 10, 0))));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        stockChangedConsumer.handle(new StockChangedEvent(
                "evt-id", "StockChanged", "2026-03-23T00:00:00Z", "product-service",
                new StockChangedEvent.StockChangedPayload(productId, "v1", 10, 0, -10, "ORDER_RESERVED", null)
        ));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        var response = elasticsearchClient.get(GetRequest.of(g -> g
                .index(indexProperties.name())
                .id(productId)
        ), Map.class);

        assertThat(response.source()).containsEntry("status", "SOLD_OUT");
        assertThat(response.source()).containsEntry("totalStock", 0);
    }
}
