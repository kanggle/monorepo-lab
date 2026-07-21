package com.example.scmplatform.demandplanning.adapter.inbound.web;

import com.example.scmplatform.demandplanning.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.demandplanning.adapter.inbound.web.controller.PolicyController;
import com.example.scmplatform.demandplanning.application.usecase.PolicyManagementUseCase;
import com.example.scmplatform.demandplanning.config.SecurityConfig;
import com.example.scmplatform.demandplanning.domain.error.PolicyNotFoundException;
import com.example.scmplatform.demandplanning.domain.model.ReorderPolicy;
import com.example.scmplatform.demandplanning.domain.model.SkuSupplierMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class PolicyControllerSliceTest {

    @Autowired MockMvc mockMvc;
    @MockBean PolicyManagementUseCase policyManagementUseCase;

    // ADR-MONO-050 D9: supplierId is a supplier CODE (String), not a UUID.
    static final String SUPPLIER_ID = "SUP-0043";

    @Test
    @WithMockUser
    void getPolicy_returns200() throws Exception {
        when(policyManagementUseCase.getPolicy("SKU-001"))
                .thenReturn(new ReorderPolicy("SKU-001", 10, 5, 100, "scm", 0, Instant.now()));

        mockMvc.perform(get("/api/demand-planning/policies/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuCode").value("SKU-001"))
                .andExpect(jsonPath("$.data.reorderPoint").value(10))
                .andExpect(jsonPath("$.data.reorderQty").value(100));
    }

    @Test
    @WithMockUser
    void getPolicy_returns404_whenNotFound() throws Exception {
        when(policyManagementUseCase.getPolicy("MISSING"))
                .thenThrow(new PolicyNotFoundException("MISSING"));

        mockMvc.perform(get("/api/demand-planning/policies/MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void upsertPolicy_returns200() throws Exception {
        when(policyManagementUseCase.upsertPolicy(eq("SKU-001"), anyInt(), anyInt(), anyInt()))
                .thenReturn(new ReorderPolicy("SKU-001", 10, 5, 100, "scm", 0, Instant.now()));

        mockMvc.perform(put("/api/demand-planning/policies/SKU-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reorderPoint\":10,\"safetyStock\":5,\"reorderQty\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reorderQty").value(100));
    }

    @Test
    @WithMockUser
    void upsertPolicy_returns422_whenInvalidInput() throws Exception {
        mockMvc.perform(put("/api/demand-planning/policies/SKU-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reorderPoint\":-1,\"safetyStock\":5,\"reorderQty\":100}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser
    void getMapping_returns200() throws Exception {
        when(policyManagementUseCase.getMapping("SKU-001"))
                .thenReturn(new SkuSupplierMapping("SKU-001", SUPPLIER_ID, 200, 7, "KRW", "scm"));

        mockMvc.perform(get("/api/demand-planning/sku-supplier-map/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuCode").value("SKU-001"))
                .andExpect(jsonPath("$.data.currency").value("KRW"));
    }

    @Test
    @WithMockUser
    void upsertMapping_returns200() throws Exception {
        when(policyManagementUseCase.upsertMapping(eq("SKU-001"), any(), anyInt(), anyInt(), any()))
                .thenReturn(new SkuSupplierMapping("SKU-001", SUPPLIER_ID, 200, 7, "KRW", "scm"));

        mockMvc.perform(put("/api/demand-planning/sku-supplier-map/SKU-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + SUPPLIER_ID + "\",\"defaultOrderQty\":200," +
                                 "\"leadTimeDays\":7,\"currency\":\"KRW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("KRW"));
    }
}
