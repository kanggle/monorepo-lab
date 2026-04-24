package com.example.search;

import com.example.search.adapter.inbound.event.ProductCreatedConsumer;
import com.example.search.adapter.inbound.event.ProductCreatedEvent;
import com.example.search.adapter.outbound.elasticsearch.IndexProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import co.elastic.clients.elasticsearch.ElasticsearchClient;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("검색 쿼리 통합 테스트")
class SearchQueryIntegrationTest {

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
    private MockMvc mockMvc;

    @Autowired
    private ProductCreatedConsumer productCreatedConsumer;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private IndexProperties indexProperties;

    private ProductCreatedEvent createdEvent(String productId, String name, String categoryId,
                                             long price, List<ProductCreatedEvent.VariantPayload> variants) {
        return new ProductCreatedEvent(
                "evt-id", "ProductCreated", "2026-03-23T00:00:00Z", "product-service",
                new ProductCreatedEvent.ProductCreatedPayload(productId, name, "설명", price, "ON_SALE", categoryId, null, variants)
        );
    }

    @Test
    @DisplayName("데이터 색인 후 검색 시 결과와 팩싯이 반환된다")
    void search_afterIndexing_returnsResultsAndFacets() throws Exception {
        String productId = "query-integ-" + System.nanoTime();
        productCreatedConsumer.handle(createdEvent(productId, "게이밍 노트북", "electronics", 1500000L,
                List.of(new ProductCreatedEvent.VariantPayload("v1", "블랙", 5, 0))));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        mockMvc.perform(get("/api/search/products")
                        .param("q", "게이밍")
                        .param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("게이밍"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.facets").exists())
                .andExpect(jsonPath("$.facets.categories").isArray())
                .andExpect(jsonPath("$.facets.priceRanges").isArray());
    }

    @Test
    @DisplayName("categoryId 필터 적용 시 해당 카테고리 상품만 반환된다")
    void search_withCategoryFilter_returnsFilteredResults() throws Exception {
        String productId1 = "cat-filter-1-" + System.nanoTime();
        String productId2 = "cat-filter-2-" + System.nanoTime();
        productCreatedConsumer.handle(createdEvent(productId1, "전자책 리더기", "electronics", 200000L, List.of()));
        productCreatedConsumer.handle(createdEvent(productId2, "전자책 표지", "accessories", 15000L, List.of()));
        elasticsearchClient.indices().refresh(r -> r.index(indexProperties.name()));

        mockMvc.perform(get("/api/search/products")
                        .param("q", "전자책")
                        .param("categoryId", "electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoryId").value("electronics"));
    }

    @Test
    @DisplayName("q 파라미터 없이 요청 시 400 반환")
    void search_withoutQ_returns400() throws Exception {
        mockMvc.perform(get("/api/search/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }
}
