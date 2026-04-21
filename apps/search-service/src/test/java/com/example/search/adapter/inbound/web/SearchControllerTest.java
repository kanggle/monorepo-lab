package com.example.search.adapter.inbound.web;

import com.example.search.application.dto.SearchProductResult;
import com.example.search.application.service.SearchProductService;
import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;
import com.example.search.adapter.inbound.web.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("SearchController 슬라이스 테스트")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchProductService searchProductService;

    @Test
    @DisplayName("q 파라미터 누락 시 400 반환")
    void search_missingQ_returns400() throws Exception {
        mockMvc.perform(get("/api/search/products"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    @DisplayName("q 파라미터가 빈 값이면 400 반환")
    void search_blankQ_returns400() throws Exception {
        mockMvc.perform(get("/api/search/products").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    @DisplayName("정상 요청 시 200과 응답 구조 반환")
    void search_validQ_returns200WithStructure() throws Exception {
        SearchProductResult result = new SearchProductResult(
                List.of(SearchDocument.of("p1", "노트북", "설명", 1000000L, "ON_SALE", "cat1", 5)),
                new FacetResult(
                        List.of(new FacetResult.CategoryFacet("cat1", 1L)),
                        List.of(new FacetResult.PriceRangeFacet(null, 10000L, 0L))
                ),
                1L
        );
        given(searchProductService.search(any())).willReturn(result);

        mockMvc.perform(get("/api/search/products").param("q", "노트북"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("노트북"))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].productId").value("p1"))
                .andExpect(jsonPath("$.facets").exists())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("size=0 요청 시 400 반환")
    void search_sizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/search/products").param("q", "노트북").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    @DisplayName("size=101 요청 시 size가 100으로 제한된 응답 반환")
    void search_sizeOver100_cappedAt100() throws Exception {
        SearchProductResult result = new SearchProductResult(
                List.of(),
                new FacetResult(List.of(), List.of()),
                0L
        );
        given(searchProductService.search(any())).willReturn(result);

        mockMvc.perform(get("/api/search/products").param("q", "노트북").param("size", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("size가 음수이면 400 반환")
    void search_negativeSize_returns400() throws Exception {
        mockMvc.perform(get("/api/search/products").param("q", "노트북").param("size", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    @DisplayName("검색 결과 0건이면 200과 빈 content 반환")
    void search_noResults_returns200WithEmptyContent() throws Exception {
        given(searchProductService.search(any())).willReturn(
                new SearchProductResult(List.of(), new FacetResult(List.of(), List.of()), 0L)
        );

        mockMvc.perform(get("/api/search/products").param("q", "없는상품xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("categoryId, minPrice, maxPrice 필터 조합 요청 시 200 반환")
    void search_withFilterCombination_returns200() throws Exception {
        given(searchProductService.search(any())).willReturn(
                new SearchProductResult(List.of(), new FacetResult(List.of(), List.of()), 0L)
        );

        mockMvc.perform(get("/api/search/products")
                        .param("q", "노트북")
                        .param("categoryId", "electronics")
                        .param("minPrice", "100000")
                        .param("maxPrice", "2000000")
                        .param("status", "ON_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("노트북"));
    }

    @Test
    @DisplayName("sort=price_asc 파라미터가 정상 처리된다")
    void search_withSortParam_returns200() throws Exception {
        given(searchProductService.search(any())).willReturn(
                new SearchProductResult(List.of(), new FacetResult(List.of(), List.of()), 0L)
        );

        mockMvc.perform(get("/api/search/products")
                        .param("q", "노트북")
                        .param("sort", "price_asc"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("page 파라미터 지정 시 응답에 page가 반영된다")
    void search_withPage_returnsCorrectPage() throws Exception {
        given(searchProductService.search(any())).willReturn(
                new SearchProductResult(List.of(), new FacetResult(List.of(), List.of()), 0L)
        );

        mockMvc.perform(get("/api/search/products")
                        .param("q", "노트북")
                        .param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3));
    }

    @Test
    @DisplayName("minPrice가 maxPrice보다 크면 400 반환")
    void search_minPriceGreaterThanMaxPrice_returns400() throws Exception {
        mockMvc.perform(get("/api/search/products")
                        .param("q", "노트북")
                        .param("minPrice", "500000")
                        .param("maxPrice", "100000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_REQUEST"));
    }

    @Test
    @DisplayName("SearchException 발생 시 503 반환")
    void search_searchException_returns503() throws Exception {
        given(searchProductService.search(any()))
                .willThrow(new com.example.search.application.exception.SearchException("ES down", new RuntimeException()));

        mockMvc.perform(get("/api/search/products").param("q", "노트북"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_UNAVAILABLE"));
    }
}
