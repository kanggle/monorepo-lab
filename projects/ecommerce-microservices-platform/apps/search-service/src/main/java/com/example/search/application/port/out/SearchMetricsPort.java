package com.example.search.application.port.out;

import java.util.function.Supplier;

public interface SearchMetricsPort {

    void incrementSearchQuery();

    <T> T recordSearchQueryDuration(Supplier<T> operation);

    void incrementZeroResults();

    void incrementIndexSync(String eventType);

    void incrementIndexSyncFailure();
}
