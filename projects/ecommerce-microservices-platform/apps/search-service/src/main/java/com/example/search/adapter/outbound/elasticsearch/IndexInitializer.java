package com.example.search.adapter.outbound.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/**
 * 애플리케이션 기동 시 Elasticsearch 상품 인덱스가 현재 스펙(nori analyzer 기반)에
 * 부합하는지 확인하고, 필요하면 마이그레이션한다.
 *
 * 마이그레이션 정책:
 * - 인덱스가 없으면: 최신 매핑으로 생성
 * - 있지만 name 필드 analyzer가 "nori_korean"이 아니면: 삭제 후 재생성
 *   (데이터는 /api/search/admin/reindex 로 복구)
 *
 * standard analyzer에서 nori로의 마이그레이션은 mapping 변경이 불가한 필드(analyzer 변경)
 * 때문에 delete-and-recreate 외에 선택지가 없다. 무중단 전환이 필요하면 별도 alias 기반
 * 롤오버가 필요하지만 현 스코프에선 다루지 않는다. ADR-005 참고.
 *
 * 인덱스 스펙 정의는 타입 안전 빌더 대신 JSON 리터럴로 둔다 — ES Java 클라이언트의
 * nori_tokenizer 직접 매핑이 버전별로 상이하고, JSON은 ES 공식 문서 예제와 1:1로
 * 대응돼 유지보수가 쉽다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitializer implements ApplicationRunner {

    private static final String KOREAN_ANALYZER = "nori_korean";

    /**
     * nori 기반 한국어 인덱스 스펙.
     *
     * - tokenizer: nori_tokenizer, decompound_mode=mixed
     *   "청바지" → ["청바지", "청", "바지"] 모두 색인되어 부분 매칭 가능
     * - analyzer: custom — tokenizer(nori_mixed) + filter(nori_part_of_speech, lowercase)
     *   조사/어미/접미사 등을 걸러내고 영문 대소문자 통일
     */
    private static final String INDEX_SPEC_JSON = """
            {
              "settings": {
                "analysis": {
                  "tokenizer": {
                    "nori_mixed": {
                      "type": "nori_tokenizer",
                      "decompound_mode": "mixed"
                    }
                  },
                  "analyzer": {
                    "nori_korean": {
                      "type": "custom",
                      "tokenizer": "nori_mixed",
                      "filter": ["nori_part_of_speech", "lowercase"]
                    }
                  }
                }
              },
              "mappings": {
                "properties": {
                  "productId":    { "type": "keyword" },
                  "name":         { "type": "text", "analyzer": "nori_korean" },
                  "description":  { "type": "text", "analyzer": "nori_korean" },
                  "price":        { "type": "long" },
                  "status":       { "type": "keyword" },
                  "categoryId":   { "type": "keyword" },
                  "totalStock":   { "type": "integer" },
                  "thumbnailUrl": { "type": "keyword", "index": false },
                  "tenantId":     { "type": "keyword" }
                }
              }
            }
            """;

    private final ElasticsearchClient elasticsearchClient;
    private final IndexProperties indexProperties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String indexName = indexProperties.name();
        boolean exists = elasticsearchClient.indices()
                .exists(ExistsRequest.of(e -> e.index(indexName)))
                .value();

        if (exists && !hasCurrentSpec(indexName)) {
            log.warn("Index '{}' exists with outdated spec (missing nori analyzer or thumbnailUrl field). "
                    + "Deleting for migration.", indexName);
            elasticsearchClient.indices().delete(DeleteIndexRequest.of(d -> d.index(indexName)));
            exists = false;
        }

        if (!exists) {
            createIndex(indexName);
            log.info("Created Elasticsearch index '{}' with nori analyzer. "
                    + "Run POST /api/search/admin/reindex to repopulate documents.", indexName);
        } else {
            log.info("Elasticsearch index '{}' already exists with current spec.", indexName);
        }
    }

    private boolean hasCurrentSpec(String indexName) throws Exception {
        GetMappingResponse resp = elasticsearchClient.indices()
                .getMapping(g -> g.index(indexName));
        IndexMappingRecord record = resp.result().get(indexName);
        if (record == null) {
            return false;
        }
        var properties = record.mappings().properties();

        // 1. name 필드가 nori_korean analyzer를 쓰는지
        Property nameProp = properties.get("name");
        if (nameProp == null || !nameProp.isText()) {
            return false;
        }
        if (!KOREAN_ANALYZER.equals(nameProp.text().analyzer())) {
            return false;
        }

        // 2. thumbnailUrl 필드가 존재하는지
        if (!properties.containsKey("thumbnailUrl")) {
            return false;
        }

        // 3. tenantId keyword 필드가 존재하는지 (TASK-BE-404)
        // tenantId is an additive keyword field — its absence means the index predates
        // multi-tenancy. The IndexInitializer will delete-and-recreate so fresh indices
        // always carry the field. Pre-existing documents without the field are coalesced
        // to the default tenant at query time (ElasticsearchFieldMapper).
        return properties.containsKey("tenantId");
    }

    private void createIndex(String indexName) throws Exception {
        CreateIndexRequest request = CreateIndexRequest.of(b -> b
                .index(indexName)
                .withJson(new StringReader(INDEX_SPEC_JSON))
        );
        elasticsearchClient.indices().create(request);
    }
}
