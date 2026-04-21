package com.example.search.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SearchDocument 단위 테스트")
class SearchDocumentTest {

    @Test
    @DisplayName("정상 생성 - 모든 필드가 올바르게 설정된다")
    void of_validFields_createsDocument() {
        SearchDocument doc = SearchDocument.of("p1", "상품명", "설명", 10000L, "ON_SALE", "cat1", 100);

        assertThat(doc.productId()).isEqualTo("p1");
        assertThat(doc.name()).isEqualTo("상품명");
        assertThat(doc.description()).isEqualTo("설명");
        assertThat(doc.price()).isEqualTo(10000L);
        assertThat(doc.status()).isEqualTo("ON_SALE");
        assertThat(doc.categoryId()).isEqualTo("cat1");
        assertThat(doc.totalStock()).isEqualTo(100);
        assertThat(doc.score()).isNull();
    }

    @Test
    @DisplayName("score 포함 생성 - score 필드가 설정된다")
    void constructor_withScore_setsScore() {
        SearchDocument doc = new SearchDocument("p1", "상품명", "설명", 10000L, "ON_SALE", "cat1", 100, null, 1.5);

        assertThat(doc.score()).isEqualTo(1.5);
    }

    @Test
    @DisplayName("가격 0인 상품 - 정상 생성된다")
    void of_priceZero_createsDocument() {
        SearchDocument doc = SearchDocument.of("p2", "무료상품", "설명", 0L, "ON_SALE", "cat1", 1);

        assertThat(doc.price()).isEqualTo(0L);
    }

    @Test
    @DisplayName("재고 0인 상품 - 정상 생성된다")
    void of_stockZero_createsDocument() {
        SearchDocument doc = SearchDocument.of("p3", "품절상품", "설명", 5000L, "SOLD_OUT", "cat1", 0);

        assertThat(doc.totalStock()).isEqualTo(0);
        assertThat(doc.status()).isEqualTo("SOLD_OUT");
    }
}
