package com.example.search.adapter.inbound.event;

import com.example.search.application.service.IndexSyncService;
import com.example.search.domain.model.SearchDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * AC-1 (TASK-BE-404): the index-sync consumer maps an inbound event's tenant_id
 * onto the SearchDocument; defaults 'ecommerce' when the event lacks one.
 */
@DisplayName("tenant_id 전파 — Kafka consumer → SearchDocument (TASK-BE-404)")
class TenantIdPropagationConsumerTest {

    // ── ProductCreatedConsumer ────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.STRICT_STUBS)
    @DisplayName("ProductCreated consumer tenant_id 전파")
    class ProductCreatedConsumerTenantTest {

        @InjectMocks
        private ProductCreatedConsumer consumer;

        @Mock
        private IndexSyncService indexSyncService;

        @Mock
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("ProductCreated: 이벤트 envelope의 tenant_id가 SearchDocument에 전파된다")
        void productCreated_tenantIdFromEnvelope_isStampedOnDocument() {
            ProductCreatedEvent event = new ProductCreatedEvent(
                    "evt-1", "ProductCreated", "2026-06-18T00:00:00Z", "product-service", "store-a",
                    new ProductCreatedEvent.ProductCreatedPayload(
                            "p1", "노트북", "설명", 1_000_000L, "ON_SALE", "cat1", null,
                            List.of(new ProductCreatedEvent.VariantPayload("v1", "블랙", 5, 0))
                    )
            );

            consumer.handle(event);

            ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
            verify(indexSyncService).upsert(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("store-a");
        }

        @Test
        @DisplayName("ProductCreated: envelope에 tenant_id 없으면 기본값 'ecommerce'로 설정된다 (D8 net-zero)")
        void productCreated_missingTenantId_defaultsToEcommerce() {
            ProductCreatedEvent event = new ProductCreatedEvent(
                    "evt-2", "ProductCreated", "2026-06-18T00:00:00Z", "product-service", null,
                    new ProductCreatedEvent.ProductCreatedPayload(
                            "p2", "마우스", "설명", 50_000L, "ON_SALE", "cat1", null, List.of()
                    )
            );

            consumer.handle(event);

            ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
            verify(indexSyncService).upsert(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("ecommerce");
        }

        @Test
        @DisplayName("ProductCreated: envelope tenant_id가 빈 문자열이면 기본값 'ecommerce'로 설정된다")
        void productCreated_blankTenantId_defaultsToEcommerce() {
            ProductCreatedEvent event = new ProductCreatedEvent(
                    "evt-3", "ProductCreated", "2026-06-18T00:00:00Z", "product-service", "  ",
                    new ProductCreatedEvent.ProductCreatedPayload(
                            "p3", "키보드", "설명", 80_000L, "ON_SALE", "cat1", null, List.of()
                    )
            );

            consumer.handle(event);

            ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
            verify(indexSyncService).upsert(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("ecommerce");
        }
    }

    // ── ProductUpdatedConsumer ────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.STRICT_STUBS)
    @DisplayName("ProductUpdated consumer tenant_id 전파")
    class ProductUpdatedConsumerTenantTest {

        @InjectMocks
        private ProductUpdatedConsumer consumer;

        @Mock
        private IndexSyncService indexSyncService;

        @Mock
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("ProductUpdated: 이벤트 envelope의 tenant_id가 SearchDocument에 전파된다")
        void productUpdated_tenantIdFromEnvelope_isStampedOnDocument() {
            ProductUpdatedEvent event = new ProductUpdatedEvent(
                    "evt-4", "ProductUpdated", "2026-06-18T00:00:00Z", "product-service", "store-b",
                    new ProductUpdatedEvent.ProductUpdatedPayload(
                            "p4", "노트북 프로", "새 설명", 1_200_000L, "ON_SALE", "cat1", null
                    )
            );

            consumer.handle(event);

            ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
            verify(indexSyncService).upsertPreservingStock(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("store-b");
        }

        @Test
        @DisplayName("ProductUpdated: envelope에 tenant_id 없으면 기본값 'ecommerce'로 설정된다")
        void productUpdated_missingTenantId_defaultsToEcommerce() {
            ProductUpdatedEvent event = new ProductUpdatedEvent(
                    "evt-5", "ProductUpdated", "2026-06-18T00:00:00Z", "product-service", null,
                    new ProductUpdatedEvent.ProductUpdatedPayload(
                            "p5", "상품", "설명", 50_000L, "ON_SALE", "cat1", null
                    )
            );

            consumer.handle(event);

            ArgumentCaptor<SearchDocument> captor = ArgumentCaptor.forClass(SearchDocument.class);
            verify(indexSyncService).upsertPreservingStock(captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("ecommerce");
        }
    }
}
