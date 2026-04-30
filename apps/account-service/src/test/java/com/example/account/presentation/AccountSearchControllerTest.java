package com.example.account.presentation;

import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import com.example.account.application.service.AccountSearchQueryService;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.AccountSearchController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountSearchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("AccountSearchController slice tests")
class AccountSearchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccountSearchQueryService accountSearchQueryService;

    @Test
    @DisplayName("GET /internal/accounts (no email) returns paginated list")
    void search_noEmail_returnsPaginatedList() throws Exception {
        var item = new AccountSearchResult.Item("acc-1", "a@example.com", "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"));
        var result = new AccountSearchResult(List.of(item), 1, 0, 20, 1);
        given(accountSearchQueryService.search(isNull(), eq(0), eq(20))).willReturn(result);

        mockMvc.perform(get("/internal/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("acc-1"))
                .andExpect(jsonPath("$.content[0].email").value("a@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /internal/accounts?page=1&size=5 returns correct page")
    void search_noEmail_pageAndSize_appliedCorrectly() throws Exception {
        var item = new AccountSearchResult.Item("acc-2", "b@example.com", "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"));
        var result = new AccountSearchResult(List.of(item), 6, 1, 5, 2);
        given(accountSearchQueryService.search(isNull(), eq(1), eq(5))).willReturn(result);

        mockMvc.perform(get("/internal/accounts?page=1&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /internal/accounts?size=101 returns 400")
    void search_sizeOverMax_returns400() throws Exception {
        given(accountSearchQueryService.search(any(), eq(0), eq(101)))
                .willThrow(new IllegalArgumentException("size must be ≤ 100"));

        mockMvc.perform(get("/internal/accounts?size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /internal/accounts?email=a@example.com returns single item")
    void search_withEmail_returnsSingleItem() throws Exception {
        var item = new AccountSearchResult.Item("acc-1", "a@example.com", "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"));
        var result = new AccountSearchResult(List.of(item), 1, 0, 20, 1);
        given(accountSearchQueryService.search(eq("a@example.com"), eq(0), eq(20))).willReturn(result);

        mockMvc.perform(get("/internal/accounts?email=a@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("acc-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /internal/accounts?email=unknown returns empty content")
    void search_withUnknownEmail_returnsEmpty() throws Exception {
        var result = new AccountSearchResult(List.of(), 0, 0, 20, 0);
        given(accountSearchQueryService.search(eq("unknown@example.com"), eq(0), eq(20))).willReturn(result);

        mockMvc.perform(get("/internal/accounts?email=unknown@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /internal/accounts/{id} returns 200 with detail")
    void detail_existingAccount_returns200() throws Exception {
        var profile = new AccountDetailResult.Profile("John Doe", "01012345678");
        var detail = new AccountDetailResult("acc-1", "a@example.com", "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"), profile);
        given(accountSearchQueryService.detail(eq("acc-1"))).willReturn(Optional.of(detail));

        mockMvc.perform(get("/internal/accounts/acc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("acc-1"))
                .andExpect(jsonPath("$.email").value("a@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /internal/accounts/{id} unknown returns 404")
    void detail_unknownAccount_returns404() throws Exception {
        given(accountSearchQueryService.detail(eq("acc-999"))).willReturn(Optional.empty());

        mockMvc.perform(get("/internal/accounts/acc-999"))
                .andExpect(status().isNotFound());
    }
}
