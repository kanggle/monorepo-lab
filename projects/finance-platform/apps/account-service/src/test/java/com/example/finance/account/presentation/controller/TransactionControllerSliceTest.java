package com.example.finance.account.presentation.controller;

import com.example.common.page.PageResult;
import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.view.TransactionView;
import com.example.finance.account.presentation.advice.GlobalExceptionHandler;
import com.example.finance.account.presentation.support.IdempotentExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link TransactionController}'s paginated
 * {@code GET /{id}/transactions} endpoint — the JSON wire-shape proof for
 * TASK-FIN-BE-067 (ADR-MONO-058 D3: {@code PageResponse}/{@code
 * TransactionPageView} → {@code com.example.common.page.PageResult}
 * adoption). Asserts the {@code data} page-wrapper shape
 * (content/page/size/totalElements/totalPages) and the {@code meta}
 * page/size/totalElements fields are unchanged by the type swap — both the
 * populated case and the zero-result (empty content) edge case
 * (architecture.md / task Edge Cases: no {@code null} vs {@code []}
 * divergence).
 */
@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TransactionControllerSliceTest {

    private static final ActorContext HOLDER =
            new ActorContext("user-1", "finance", Set.of());

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AccountApplicationService service;
    @MockitoBean
    IdempotentExecution idempotency;

    @BeforeEach
    void setUp() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(HOLDER, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private TransactionView view() {
        return new TransactionView("txn-1", "TOPUP", "COMPLETED", "150000", "KRW",
                null, null, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"));
    }

    @Test
    @DisplayName("GET /{id}/transactions → 200 PageResult wire shape (content/page/size/totalElements/totalPages + meta)")
    void listPopulatedPage() throws Exception {
        when(service.listTransactions(anyString(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(view()), 0, 20, 1, 1));

        mockMvc.perform(get("/api/finance/accounts/acc-1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].transactionId").value("txn-1"))
                .andExpect(jsonPath("$.data.content[0].type").value("TOPUP"))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].money.amount").value("150000"))
                .andExpect(jsonPath("$.data.content[0].money.currency").value("KRW"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /{id}/transactions → 200 empty page ([] content, zero totals, no null)")
    void listEmptyPage() throws Exception {
        when(service.listTransactions(anyString(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/finance/accounts/acc-1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }
}
