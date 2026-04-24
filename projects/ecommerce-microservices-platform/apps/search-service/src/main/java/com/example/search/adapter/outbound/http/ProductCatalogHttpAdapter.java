package com.example.search.adapter.outbound.http;

import com.example.search.application.port.out.ProductCatalogPort;
import com.example.search.domain.model.SearchDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * product-service REST API에서 상품 카탈로그를 가져오는 어댑터.
 *
 * - 리스트 조회(/api/products)로 productId를 페이지 단위로 순회
 * - 각 productId에 대해 상세 조회(/api/products/{id})로 variant 정보까지 획득
 *   (variant의 stock 합산 필요)
 */
@Slf4j
@Component
public class ProductCatalogHttpAdapter implements ProductCatalogPort {

    private final RestClient restClient;

    public ProductCatalogHttpAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${catalog.product-service-url:http://product-service:8082}") String productServiceUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(productServiceUrl).build();
    }

    @Override
    public List<SearchDocument> fetchAll(int batchSize) {
        List<SearchDocument> all = new ArrayList<>();
        int page = 0;
        int totalPages;

        do {
            PageResponse listPage = fetchList(page, batchSize);
            if (listPage == null || listPage.content() == null) {
                break;
            }
            for (SummaryResponse summary : listPage.content()) {
                DetailResponse detail = fetchDetail(summary.id());
                if (detail == null) {
                    continue;
                }
                int totalStock = detail.variants() == null ? 0
                        : detail.variants().stream().mapToInt(v -> v.stock).sum();
                all.add(SearchDocument.of(
                        detail.id(),
                        detail.name(),
                        detail.description(),
                        detail.price(),
                        detail.status(),
                        detail.categoryId(),
                        totalStock,
                        detail.thumbnailUrl()
                ));
            }
            totalPages = listPage.totalPages();
            page++;
        } while (page < totalPages);

        log.info("Fetched {} products from catalog for reindex", all.size());
        return all;
    }

    private PageResponse fetchList(int page, int size) {
        return restClient.get()
                .uri("/api/products?page={page}&size={size}", page, size)
                .retrieve()
                .body(PageResponse.class);
    }

    private DetailResponse fetchDetail(String productId) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(DetailResponse.class);
        } catch (Exception e) {
            log.warn("Failed to fetch product detail id={}, skipping. cause={}", productId, e.getMessage());
            return null;
        }
    }

    // Product API 응답 매핑용 내부 DTO.
    // product-service의 프로덕션 DTO와 결합하지 않고 필드 변경에 유연하게 대응하기 위해 로컬에 정의.

    record PageResponse(List<SummaryResponse> content, int totalPages) {
        PageResponse {
            content = content == null ? List.of() : content;
        }
    }

    record SummaryResponse(String id) {}

    record DetailResponse(
            String id,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            String thumbnailUrl,
            List<VariantResponse> variants
    ) {}

    // stock만 reindex에 필요하지만, product-service DTO 변경에 tolerant하게 대응하기 위해
    // 나머지 필드(id/optionName/additionalPrice)도 역직렬화 대상에 포함시켜 둔다.
    record VariantResponse(String id, String optionName, int stock, long additionalPrice) {}
}
