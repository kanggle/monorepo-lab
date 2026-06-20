package com.example.batch.application;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.model.BatchJobStatus;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import com.example.batch.infrastructure.client.ProductServiceClient;
import com.example.batch.infrastructure.client.ProductServiceClient.ProductPageResponse;
import com.example.batch.infrastructure.client.ProductServiceClient.ProductSummary;
import com.example.batch.infrastructure.client.SearchServiceClient;
import com.example.batch.infrastructure.client.SearchServiceClient.SearchHit;
import com.example.batch.infrastructure.client.SearchServiceClient.SearchResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SearchIndexConsistencyJob} (TASK-BE-409 / AC-5).
 *
 * <p>Calls {@code execute()} directly — never through the scheduler — to bypass ShedLock
 * (the {@code lockAtLeastFor} trap: subsequent in-process invocations would silently no-op).
 *
 * <p>Covers:
 * <ul>
 *   <li>Empty catalog → 0 inconsistencies, COMPLETED history</li>
 *   <li>3 products, 1 missing from search → counter +1, COMPLETED history</li>
 *   <li>Search client throws → FAILED history recorded, exception NOT propagated</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("SearchIndexConsistencyJob 단위 테스트")
class SearchIndexConsistencyJobTest {

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private SearchServiceClient searchServiceClient;

    @Mock
    private BatchJobExecutionRepository executionRepository;

    private MeterRegistry meterRegistry;
    private SearchIndexConsistencyJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        job = new SearchIndexConsistencyJob(
                productServiceClient, searchServiceClient, executionRepository, meterRegistry);
        // Enable job (default is true; set explicitly for clarity)
        ReflectionTestUtils.setField(job, "enabled", true);

        // Default: save returns a reconstituted execution with a generated ID
        when(executionRepository.save(any(BatchJobExecution.class)))
                .thenAnswer(invocation -> {
                    BatchJobExecution arg = invocation.getArgument(0);
                    // Reconstitute with a fake ID to simulate JPA identity assignment
                    return BatchJobExecution.reconstitute(
                            arg.getId() != null ? arg.getId() : 1L,
                            arg.getJobName(),
                            arg.getStatus(),
                            arg.getStartedAt(),
                            arg.getFinishedAt(),
                            arg.getErrorMessage());
                });
    }

    @Test
    @DisplayName("빈 카탈로그 → inconsistency 0, COMPLETED 히스토리")
    void emptyProductCatalog_zeroInconsistencies_completed() {
        // Arrange: product-service returns empty first page
        when(productServiceClient.listOnSale(0, 50))
                .thenReturn(new ProductPageResponse(List.of(), 0, 50, 0));

        // Act
        job.execute();

        // Assert: counter stays at zero
        Counter counter = meterRegistry.find(SearchIndexConsistencyJob.INCONSISTENCY_COUNTER_NAME).counter();
        double count = counter != null ? counter.count() : 0.0;
        assertThat(count).isEqualTo(0.0);

        // Assert: execution saved twice (start RUNNING + complete COMPLETED)
        // WARNING fix: capture the second save and assert COMPLETED status — guards against a
        // regression that records FAILED on the happy path.
        org.mockito.ArgumentCaptor<BatchJobExecution> captor =
                org.mockito.ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<BatchJobExecution> saved = captor.getAllValues();
        assertThat(saved.get(1).getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("3개 상품 중 1개 검색 누락 → 메트릭 +1, COMPLETED 히스토리")
    void threeProducts_oneMissingInSearch_metricPlusOne_completed() {
        // Arrange
        UUID product1 = UUID.randomUUID();
        UUID product2 = UUID.randomUUID();
        UUID product3 = UUID.randomUUID();

        List<ProductSummary> products = List.of(
                new ProductSummary(product1, "Widget Alpha", "ON_SALE", 10000L, null, null, null),
                new ProductSummary(product2, "Widget Beta", "ON_SALE", 20000L, null, null, null),
                new ProductSummary(product3, "Widget Gamma", "ON_SALE", 30000L, null, null, null)
        );

        when(productServiceClient.listOnSale(0, 50))
                .thenReturn(new ProductPageResponse(products, 0, 50, 3));

        // product1 found in search
        when(searchServiceClient.searchByName("Widget Alpha"))
                .thenReturn(new SearchResponse("Widget Alpha",
                        List.of(new SearchHit(product1, "Widget Alpha", 10000L, "ON_SALE", null, null, 1.5)), 0, 20, 1L));

        // product2 found in search
        when(searchServiceClient.searchByName("Widget Beta"))
                .thenReturn(new SearchResponse("Widget Beta",
                        List.of(new SearchHit(product2, "Widget Beta", 20000L, "ON_SALE", null, null, 1.2)), 0, 20, 1L));

        // product3 NOT found in search (empty results — index drift)
        when(searchServiceClient.searchByName("Widget Gamma"))
                .thenReturn(new SearchResponse("Widget Gamma", List.of(), 0, 20, 0L));

        // Act
        job.execute();

        // Assert: exactly 1 inconsistency counted
        Counter counter = meterRegistry.find(SearchIndexConsistencyJob.INCONSISTENCY_COUNTER_NAME).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        // Assert: execution saved twice (start + complete) and SECOND save has COMPLETED status.
        // WARNING fix: explicit COMPLETED assertion catches a regression that saves FAILED on
        // the happy path while save() count alone would still pass.
        org.mockito.ArgumentCaptor<BatchJobExecution> captor =
                org.mockito.ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<BatchJobExecution> saved = captor.getAllValues();
        assertThat(saved.get(1).getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("검색 서비스 예외 → FAILED 히스토리 기록, 예외 비전파")
    void searchClientThrows_failedHistoryRecorded_exceptionNotPropagated() {
        // Arrange: first page has one product
        UUID productId = UUID.randomUUID();
        when(productServiceClient.listOnSale(0, 50))
                .thenReturn(new ProductPageResponse(
                        List.of(new ProductSummary(productId, "Broken Widget", "ON_SALE", 5000L, null, null, null)),
                        0, 50, 1));

        // search-service is down
        when(searchServiceClient.searchByName("Broken Widget"))
                .thenThrow(new RuntimeException("search-service connection refused"));

        // Act — must NOT throw
        job.execute();

        // Assert: FAILED history was saved (two saves: start + fail)
        org.mockito.ArgumentCaptor<BatchJobExecution> captor =
                org.mockito.ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        List<BatchJobExecution> saved = captor.getAllValues();
        BatchJobExecution lastSave = saved.get(saved.size() - 1);
        assertThat(lastSave.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(lastSave.getErrorMessage()).contains("search-service connection refused");
    }
}
