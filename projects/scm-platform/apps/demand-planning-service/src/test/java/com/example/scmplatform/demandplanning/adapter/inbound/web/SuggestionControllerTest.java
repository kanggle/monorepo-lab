package com.example.scmplatform.demandplanning.adapter.inbound.web;

import com.example.scmplatform.demandplanning.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.demandplanning.adapter.inbound.web.controller.SuggestionController;
import com.example.scmplatform.demandplanning.application.usecase.ApproveSuggestionUseCase;
import com.example.scmplatform.demandplanning.application.usecase.SuggestionQueryUseCase;
import com.example.scmplatform.demandplanning.config.SecurityConfig;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.error.SuggestionNotFoundException;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SuggestionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class SuggestionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SuggestionQueryUseCase suggestionQueryUseCase;
    @MockBean ApproveSuggestionUseCase approveSuggestionUseCase;

    static final UUID SUGGESTION_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000001");
    static final UUID WAREHOUSE_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000002");
    static final UUID SUPPLIER_ID = UUID.fromString("0192cccc-0000-0000-0000-000000000003");

    @Test
    @WithMockUser
    void listSuggestions_returns200_withPageMeta() throws Exception {
        ReorderSuggestion s = ReorderSuggestion.raiseFromAlert(
                SUGGESTION_ID, "SKU-001", WAREHOUSE_ID, SUPPLIER_ID,
                100, UUID.randomUUID(), 5, "scm", Instant.now());

        when(suggestionQueryUseCase.listSuggestions(anyString(), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(s), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/demand-planning/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(SUGGESTION_ID.toString()))
                .andExpect(jsonPath("$.data[0].status").value("SUGGESTED"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void getSuggestion_returns200() throws Exception {
        ReorderSuggestion s = ReorderSuggestion.raiseFromAlert(
                SUGGESTION_ID, "SKU-001", WAREHOUSE_ID, SUPPLIER_ID,
                100, UUID.randomUUID(), 5, "scm", Instant.now());
        when(suggestionQueryUseCase.getById(SUGGESTION_ID)).thenReturn(s);

        mockMvc.perform(get("/api/demand-planning/suggestions/" + SUGGESTION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SUGGESTION_ID.toString()));
    }

    @Test
    @WithMockUser
    void getSuggestion_returns404_whenNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        when(suggestionQueryUseCase.getById(missing))
                .thenThrow(new SuggestionNotFoundException(missing));

        mockMvc.perform(get("/api/demand-planning/suggestions/" + missing))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUGGESTION_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void dismissSuggestion_returns200() throws Exception {
        ReorderSuggestion dismissed = ReorderSuggestion.reconstitute(
                SUGGESTION_ID, "SKU-001", WAREHOUSE_ID, SUPPLIER_ID,
                100, SuggestionStatus.DISMISSED, SuggestionSource.ALERT,
                null, 5, null, "scm", 1, Instant.now(), Instant.now());
        when(suggestionQueryUseCase.dismiss(eq(SUGGESTION_ID), any())).thenReturn(dismissed);

        mockMvc.perform(post("/api/demand-planning/suggestions/" + SUGGESTION_ID + "/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"stale\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }

    @Test
    void listSuggestions_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/demand-planning/suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void approve_returns200_materialized() throws Exception {
        UUID poId = UUID.fromString("0192cccc-0000-0000-0000-0000000000aa");
        when(approveSuggestionUseCase.approve(eq(SUGGESTION_ID), any()))
                .thenReturn(new ApproveSuggestionUseCase.ApproveResult(
                        SUGGESTION_ID, SuggestionStatus.MATERIALIZED, poId, "DRAFT"));

        mockMvc.perform(post("/api/demand-planning/suggestions/" + SUGGESTION_ID + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SUGGESTION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("MATERIALIZED"))
                .andExpect(jsonPath("$.data.poId").value(poId.toString()))
                .andExpect(jsonPath("$.data.poStatus").value("DRAFT"));
    }

    @Test
    @WithMockUser
    void approve_returns422_whenSkuUnmapped() throws Exception {
        when(approveSuggestionUseCase.approve(eq(SUGGESTION_ID), any()))
                .thenThrow(new SkuSupplierUnmappedException("SKU-APPLE-001"));

        mockMvc.perform(post("/api/demand-planning/suggestions/" + SUGGESTION_ID + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SKU_SUPPLIER_UNMAPPED"));
    }
}
