package com.example.search.adapter.inbound.web;

import com.example.search.adapter.inbound.web.dto.SearchProductResponse;
import com.example.search.application.dto.SearchProductQuery;
import com.example.search.application.dto.SearchProductResult;
import com.example.search.application.port.in.SearchProductUseCase;
import com.example.search.domain.model.SearchFilter;
import com.example.search.domain.model.SearchSort;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/search")
@Validated
public class SearchController {

    private final SearchProductUseCase searchProductUseCase;

    @GetMapping("/products")
    public ResponseEntity<SearchProductResponse> search(
            @RequestParam @NotBlank(message = "q parameter is required and must not be empty") String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Positive(message = "size must be greater than 0") int size
    ) {

        SearchFilter filter = SearchFilter.of(q, categoryId, minPrice, maxPrice, status);
        SearchSort searchSort = SearchSort.from(sort);
        SearchProductQuery query = new SearchProductQuery(filter, searchSort, page, size);

        SearchProductResult result = searchProductUseCase.search(query);
        return ResponseEntity.ok(SearchProductResponse.from(q, result, page, query.size()));
    }
}
