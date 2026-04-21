package com.example.search.application.port.in;

import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;

public interface SearchProductUseCase {

    SearchProductResult search(SearchProductQuery query);
}
