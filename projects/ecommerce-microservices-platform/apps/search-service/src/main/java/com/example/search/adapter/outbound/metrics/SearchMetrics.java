package com.example.search.adapter.outbound.metrics;

import com.example.search.application.port.out.SearchMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class SearchMetrics implements SearchMetricsPort {

    private final Counter searchQueryTotal;
    private final Timer searchQueryDuration;
    private final Counter searchZeroResultsTotal;
    private final Counter indexSyncCreated;
    private final Counter indexSyncUpdated;
    private final Counter indexSyncDeleted;
    private final Counter indexSyncFailureTotal;

    public SearchMetrics(MeterRegistry registry) {
        this.searchQueryTotal = Counter.builder("search_query_total")
                .description("Total search queries executed")
                .register(registry);

        this.searchQueryDuration = Timer.builder("search_query_duration_seconds")
                .description("Search query latency")
                .register(registry);

        this.searchZeroResultsTotal = Counter.builder("search_zero_results_total")
                .description("Total queries returning zero results")
                .register(registry);

        this.indexSyncCreated = Counter.builder("search_index_sync_total")
                .description("Total index sync operations by event type")
                .tag("event_type", "created")
                .register(registry);

        this.indexSyncUpdated = Counter.builder("search_index_sync_total")
                .description("Total index sync operations by event type")
                .tag("event_type", "updated")
                .register(registry);

        this.indexSyncDeleted = Counter.builder("search_index_sync_total")
                .description("Total index sync operations by event type")
                .tag("event_type", "deleted")
                .register(registry);

        this.indexSyncFailureTotal = Counter.builder("search_index_sync_failure_total")
                .description("Total index sync failures")
                .register(registry);
    }

    @Override
    public void incrementSearchQuery() {
        searchQueryTotal.increment();
    }

    @Override
    public <T> T recordSearchQueryDuration(Supplier<T> operation) {
        return searchQueryDuration.record(operation);
    }

    @Override
    public void incrementZeroResults() {
        searchZeroResultsTotal.increment();
    }

    @Override
    public void incrementIndexSync(String eventType) {
        switch (eventType) {
            case "created" -> indexSyncCreated.increment();
            case "deleted" -> indexSyncDeleted.increment();
            default -> indexSyncUpdated.increment();
        }
    }

    @Override
    public void incrementIndexSyncFailure() {
        indexSyncFailureTotal.increment();
    }
}
