package com.example.search.application.service;

import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.application.port.out.SearchMetricsPort;
import com.example.search.application.port.out.SearchQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchProductService implements SearchProductUseCase {

    private final SearchQueryPort searchQueryPort;
    private final SearchMetricsPort searchMetrics;

    public SearchProductResult search(SearchProductQuery query) {
        log.info("Searching products keyword={}, page={}, size={}",
                query.filter().keyword(), query.page(), query.size());

        SearchProductResult result = searchMetrics.recordSearchQueryDuration(() -> searchQueryPort.search(query));
        searchMetrics.incrementSearchQuery();
        if (result != null && result.content().isEmpty()) {
            searchMetrics.incrementZeroResults();
        }
        return result;
    }
}
