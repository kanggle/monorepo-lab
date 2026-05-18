package com.example.finance.account.presentation.controller;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.domain.error.DomainErrors;
import com.example.finance.account.presentation.advice.GlobalExceptionHandler;
import com.example.finance.account.presentation.support.IdempotentExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link AccountController} + the
 * {@link GlobalExceptionHandler} error envelope. Security filters are bypassed
 * ({@code addFilters = false}); the {@link ActorContext} is placed directly in
 * the {@link SecurityContextHolder}. {@link IdempotentExecution} is stubbed to
 * pass through to the action (idempotency mechanics are unit-tested
 * separately).
 */
@WebMvcTest(AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccountControllerSliceTest {

    private static final ActorContext HOLDER =
            new ActorContext("user-1", "finance", java.util.Set.of());

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AccountApplicationService service;
    @MockBean
    IdempotentExecution idempotency;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(HOLDER, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        // Pass-through: invoke the supplied action directly.
        when(idempotency.run(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> ((Supplier<ResponseEntity<?>>) inv.getArgument(4)).get());
    }

    private AccountView pendingView() {
        return new AccountView("acc-1", "PENDING_KYC", "KRW", "NONE",
                List.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("POST /api/finance/accounts → 201 with PENDING_KYC")
    void open201() throws Exception {
        when(service.openAccount(any())).thenReturn(pendingView());
        mockMvc.perform(post("/api/finance/accounts")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerRef\":\"cust-1\",\"currency\":\"KRW\",\"kycLevel\":\"NONE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").value("acc-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING_KYC"));
    }

    @Test
    @DisplayName("POST /api/finance/accounts without Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void openMissingIdemKey() throws Exception {
        mockMvc.perform(post("/api/finance/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerRef\":\"cust-1\",\"currency\":\"KRW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /api/finance/accounts unsupported currency → 422 CURRENCY_MISMATCH")
    void openUnsupportedCurrency() throws Exception {
        when(service.openAccount(any())).thenThrow(
                new com.example.finance.account.domain.money.Currency
                        .UnsupportedCurrencyException("unsupported currency: XBT"));
        mockMvc.perform(post("/api/finance/accounts")
                        .header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerRef\":\"cust-1\",\"currency\":\"XBT\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    @DisplayName("GET /api/finance/accounts/{id} unknown → 404 ACCOUNT_NOT_FOUND")
    void getNotFound() throws Exception {
        when(service.getAccount(Mockito.eq("missing"), any()))
                .thenThrow(new DomainErrors.AccountNotFoundException("Account not found: missing"));
        mockMvc.perform(get("/api/finance/accounts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /kyc/upgrade by non-operator → 403 PERMISSION_DENIED")
    void kycUpgradeForbidden() throws Exception {
        when(service.upgradeKyc(any())).thenThrow(
                new DomainErrors.PermissionDeniedException("KYC upgrade is operator-only"));
        mockMvc.perform(post("/api/finance/accounts/acc-1/kyc/upgrade")
                        .header("Idempotency-Key", "k3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toLevel\":\"BASIC\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
