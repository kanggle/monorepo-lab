package com.example.search.adapter.outbound.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchMetricsTest {

    private MeterRegistry registry;
    private SearchMetrics searchMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        searchMetrics = new SearchMetrics(registry);
    }

    @Test
    @DisplayName("검색 쿼리 실행 시 search_query_total이 증가한다")
    void incrementSearchQuery_incrementsCounter() {
        searchMetrics.incrementSearchQuery();
        searchMetrics.incrementSearchQuery();

        assertThat(registry.counter("search_query_total").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("검색 결과가 0건일 때 search_zero_results_total이 증가한다")
    void incrementZeroResults_incrementsCounter() {
        searchMetrics.incrementZeroResults();

        assertThat(registry.counter("search_zero_results_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("인덱스 동기화 시 event_type별 search_index_sync_total이 증가한다")
    void incrementIndexSync_incrementsCounterByEventType() {
        searchMetrics.incrementIndexSync("created");
        searchMetrics.incrementIndexSync("updated");
        searchMetrics.incrementIndexSync("deleted");
        searchMetrics.incrementIndexSync("created");

        assertThat(registry.counter("search_index_sync_total", "event_type", "created").count()).isEqualTo(2.0);
        assertThat(registry.counter("search_index_sync_total", "event_type", "updated").count()).isEqualTo(1.0);
        assertThat(registry.counter("search_index_sync_total", "event_type", "deleted").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("인덱스 동기화 실패 시 search_index_sync_failure_total이 증가한다")
    void incrementIndexSyncFailure_incrementsCounter() {
        searchMetrics.incrementIndexSyncFailure();

        assertThat(registry.counter("search_index_sync_failure_total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordSearchQueryDuration 호출 시 타이머에 기록되고 결과를 반환한다")
    void recordSearchQueryDuration_recordsTimerAndReturnsResult() {
        String result = searchMetrics.recordSearchQueryDuration(() -> "result");

        assertThat(result).isEqualTo("result");
        assertThat(registry.timer("search_query_duration_seconds").count()).isEqualTo(1L);
    }
}
