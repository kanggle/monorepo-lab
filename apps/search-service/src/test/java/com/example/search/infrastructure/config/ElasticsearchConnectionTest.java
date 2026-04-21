package com.example.search.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.example.search.adapter.outbound.elasticsearch.IndexProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("Elasticsearch 연결 통합 테스트")
class ElasticsearchConnectionTest {

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
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private IndexProperties indexProperties;

    @Test
    @DisplayName("Elasticsearch 연결이 성공하고 products 인덱스가 생성된다")
    void elasticsearchConnection_onStartup_indexCreated() throws Exception {
        boolean exists = elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexProperties.name())))
                .value();

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Elasticsearch 클러스터 상태를 확인할 수 있다")
    void elasticsearchClient_clusterHealth_available() throws Exception {
        var health = elasticsearchClient.cluster().health();

        assertThat(health.numberOfNodes()).isPositive();
    }
}
