package com.example.search.contract;

import com.example.search.adapter.inbound.web.SearchController;
import com.example.search.application.dto.SearchProductResult;
import com.example.search.application.service.SearchProductService;
import com.example.search.domain.model.FacetResult;
import com.example.search.domain.model.SearchDocument;
import com.example.search.adapter.inbound.web.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;

import static com.example.search.contract.ContractTestHelper.assertFieldsMatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * search-service API 응답 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/http/search-api.md
 */
@WebMvcTest(SearchController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("Search API 컨트랙트 테스트 — specs/contracts/http/search-api.md")
class SearchApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SearchProductService searchProductService;

    private static final String SPEC_REF = "specs/contracts/http/search-api.md";

    // ─── GET /api/search/products — 200 ─────────────────────────────────

    @Test
    @DisplayName("GET /api/search/products 응답은 {query, content, facets, page, size, totalElements}만 포함한다")
    void searchProducts_response_containsSpecFields() throws Exception {
        SearchProductResult result = new SearchProductResult(
                List.of(SearchDocument.of("p1", "노트북", "설명", 1000000L, "ON_SALE", "cat1", 5)),
                new FacetResult(
                        List.of(new FacetResult.CategoryFacet("cat1", 1L)),
                        List.of(new FacetResult.PriceRangeFacet(null, 10000L, 0L))
                ),
                1L
        );
        given(searchProductService.search(any())).willReturn(result);

        MvcResult mvcResult = mockMvc.perform(get("/api/search/products").param("q", "노트북"))
                .andExpect(status().isOk())
                .andReturn();

        String json = mvcResult.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(json);

        assertFieldsMatch(root, Set.of("query", "content", "facets", "page", "size", "totalElements"),
                SPEC_REF + " GET /api/search/products 200");

        JsonNode item = root.get("content").get(0);
        assertFieldsMatch(item, Set.of("productId", "name", "price", "status", "thumbnailUrl", "categoryId", "score"),
                SPEC_REF + " GET /api/search/products 200 content[]");

        JsonNode facets = root.get("facets");
        assertFieldsMatch(facets, Set.of("categories", "priceRanges"),
                SPEC_REF + " GET /api/search/products 200 facets");

        JsonNode categoryFacet = facets.get("categories").get(0);
        assertFieldsMatch(categoryFacet, Set.of("id", "name", "count"),
                SPEC_REF + " GET /api/search/products 200 facets.categories[]");

        JsonNode priceRange = facets.get("priceRanges").get(0);
        assertFieldsMatch(priceRange, Set.of("min", "max", "count"),
                SPEC_REF + " GET /api/search/products 200 facets.priceRanges[]");
    }

    // ─── Error Response Format ──────────────────────────────────────────

    @Test
    @DisplayName("에러 응답은 {code, message, timestamp}만 포함한다")
    void errorResponse_containsOnlyCodeMessageTimestamp() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/search/products"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("code", "message", "timestamp"),
                "specs/platform/error-handling.md error format");
    }
}
