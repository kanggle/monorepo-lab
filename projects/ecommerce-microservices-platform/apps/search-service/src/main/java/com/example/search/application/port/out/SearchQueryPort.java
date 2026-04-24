package com.example.search.application.port.out;

import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;

public interface SearchQueryPort {

    SearchProductResult search(SearchProductQuery query);
}
