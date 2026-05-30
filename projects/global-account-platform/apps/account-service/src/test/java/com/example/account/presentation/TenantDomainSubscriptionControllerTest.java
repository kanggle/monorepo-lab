package com.example.account.presentation;

import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.application.service.TenantDomainSubscriptionQueryUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.TenantDomainSubscriptionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @DisplayName("GET /internal/tenant-domain-subscriptions → 200 with items[]")
    void list_returnsItems() throws Exception {
        given(queryUseCase.listActive(isNull())).willReturn(List.of(
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
        given(queryUseCase.listActive(eq("wms"))).willReturn(List.of(
                new TenantDomainSubscriptionResult("wms", "wms")));

        mockMvc.perform(get("/internal/tenant-domain-subscriptions").param("domainKey", "wms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].domainKey").value("wms"));
    }
}
