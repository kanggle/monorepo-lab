package com.example.account.presentation;

import com.example.account.application.result.SubscriptionMutationResult;
import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.application.service.TenantDomainSubscriptionMutationUseCase;
import com.example.account.application.service.TenantDomainSubscriptionQueryUseCase;
import com.example.account.domain.tenant.IllegalSubscriptionTransitionException;
import com.example.account.domain.tenant.SubscriptionStatus;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.TenantDomainSubscriptionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantDomainSubscriptionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("TenantDomainSubscriptionController slice tests")
class TenantDomainSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantDomainSubscriptionQueryUseCase queryUseCase;

    @MockitoBean
    private TenantDomainSubscriptionMutationUseCase mutationUseCase;

    @Test
    @DisplayName("GET /internal/tenant-domain-subscriptions → 200 with items[]")
    void list_returnsItems() throws Exception {
        given(queryUseCase.listActive(isNull(), isNull())).willReturn(List.of(
                new TenantDomainSubscriptionResult("wms", "wms"),
                new TenantDomainSubscriptionResult("scm", "scm")));

        mockMvc.perform(get("/internal/tenant-domain-subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].tenantId").value("wms"))
                .andExpect(jsonPath("$.items[0].domainKey").value("wms"))
                .andExpect(jsonPath("$.items[1].tenantId").value("scm"));
    }

    @Test
    @DisplayName("GET ?domainKey=wms → forwards filter to the use-case")
    void list_withDomainFilter() throws Exception {
        given(queryUseCase.listActive(eq("wms"), isNull())).willReturn(List.of(
                new TenantDomainSubscriptionResult("wms", "wms")));

        mockMvc.perform(get("/internal/tenant-domain-subscriptions").param("domainKey", "wms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].domainKey").value("wms"));
    }

    @Test
    @DisplayName("GET ?tenantId=acme → forwards tenantId reverse-lookup to the use-case (BE-324)")
    void list_withTenantIdFilter() throws Exception {
        given(queryUseCase.listActive(isNull(), eq("acme"))).willReturn(List.of(
                new TenantDomainSubscriptionResult("acme", "finance"),
                new TenantDomainSubscriptionResult("acme", "wms")));

        mockMvc.perform(get("/internal/tenant-domain-subscriptions").param("tenantId", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].tenantId").value("acme"))
                .andExpect(jsonPath("$.items[0].domainKey").value("finance"));
    }

    // ── TASK-BE-342 (ADR-MONO-023 step 2a): mutation endpoints ───────────────

    @Test
    @DisplayName("POST → 201 with the created subscription (previousStatus null)")
    void subscribe_created() throws Exception {
        given(mutationUseCase.subscribe(eq("acme"), eq("scm"), isNull(), any(), any(), any()))
                .willReturn(new SubscriptionMutationResult(
                        "acme", "scm", null, SubscriptionStatus.ACTIVE,
                        Instant.parse("2026-06-10T00:00:00Z")));

        mockMvc.perform(post("/internal/tenant-domain-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme\",\"domainKey\":\"scm\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.previousStatus").doesNotExist());
    }

    @Test
    @DisplayName("PATCH → 200 with previous/current status (suspend)")
    void changeStatus_ok() throws Exception {
        given(mutationUseCase.changeStatus(eq("acme"), eq("wms"),
                eq(SubscriptionStatus.SUSPENDED), any(), any(), any()))
                .willReturn(new SubscriptionMutationResult(
                        "acme", "wms", SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED,
                        Instant.parse("2026-06-10T00:00:00Z")));

        mockMvc.perform(patch("/internal/tenant-domain-subscriptions/acme/wms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"past due\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("SUSPENDED"));
    }

    @Test
    @DisplayName("PATCH illegal transition → 409 SUBSCRIPTION_TRANSITION_INVALID")
    void changeStatus_illegal_409() throws Exception {
        given(mutationUseCase.changeStatus(eq("acme"), eq("wms"),
                eq(SubscriptionStatus.ACTIVE), any(), any(), any()))
                .willThrow(new IllegalSubscriptionTransitionException(
                        "acme", "wms", SubscriptionStatus.CANCELLED, SubscriptionStatus.ACTIVE));

        mockMvc.perform(patch("/internal/tenant-domain-subscriptions/acme/wms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_TRANSITION_INVALID"));
    }
}
