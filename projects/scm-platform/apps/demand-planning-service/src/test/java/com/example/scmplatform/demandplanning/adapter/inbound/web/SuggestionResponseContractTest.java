package com.example.scmplatform.demandplanning.adapter.inbound.web;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.scmplatform.demandplanning.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.demandplanning.adapter.inbound.web.controller.SuggestionController;
import com.example.scmplatform.demandplanning.application.usecase.ApproveSuggestionUseCase;
import com.example.scmplatform.demandplanning.application.usecase.SuggestionQueryUseCase;
import com.example.scmplatform.demandplanning.config.SecurityConfig;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionSource;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-SCM-BE-062 — the first contract test for {@code demand-planning-api.md}
 * § {@code GET /api/v1/demand-planning/suggestions} (row shape). Before this the only
 * contract test in the service pinned the error envelope; nothing pinned the suggestion row.
 *
 * <p>What it pins, through the real MVC + Jackson stack (not a hand-built ObjectMapper):
 * <ul>
 *   <li><b>The key set.</b> A fully-populated row emits exactly the contract's fields — no
 *       more (e.g. the internal {@code destinationNodeType}, {@code tenantId}, {@code version}
 *       never leak) and no fewer.</li>
 *   <li><b>Absent, not null.</b> The contract says a null value is an omitted key
 *       ({@code @JsonInclude(NON_NULL)}). A minimal row (no warehouse code, no snapshot, not
 *       materialized) must carry no {@code null}-valued key at all.</li>
 *   <li><b>{@code reorderPointAtTrigger} is an integer</b> when present.</li>
 * </ul>
 */
@WebMvcTest(SuggestionController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("demand-planning-api.md § GET /suggestions 행 모양 계약 (TASK-SCM-BE-062)")
class SuggestionResponseContractTest {

    /** demand-planning-api.md § GET /suggestions — the documented row fields. */
    static final Set<String> CONTRACT_FIELDS = new TreeSet<>(List.of(
            "id", "skuCode", "warehouseId", "warehouseCode", "supplierId", "suggestedQty",
            "status", "source", "triggerAvailableQty", "reorderPointAtTrigger",
            "materializedPoId", "createdAt"));

    /** Present on every row (non-nullable in the domain). */
    static final Set<String> ALWAYS_PRESENT = new TreeSet<>(List.of(
            "id", "skuCode", "warehouseId", "supplierId", "suggestedQty", "status", "source",
            "createdAt"));

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SuggestionQueryUseCase suggestionQueryUseCase;
    @MockBean ApproveSuggestionUseCase approveSuggestionUseCase;

    private JsonNode firstRow(ReorderSuggestion s) throws Exception {
        when(suggestionQueryUseCase.listSuggestions(anyString(), eq(null), eq(null), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(s), 0, 20, 1, 1));
        String body = mockMvc.perform(get("/api/demand-planning/suggestions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").get(0);
    }

    private static Set<String> keys(JsonNode row) {
        Set<String> keys = new TreeSet<>();
        row.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Test
    @WithMockUser(roles = "SCM_DEMAND_PLANNING_READ")
    @DisplayName("값이 다 찬 행 = 계약 필드 집합과 정확히 같다 (내부 필드 누출 없음)")
    void fullyPopulatedRow_emitsExactlyTheContractFields() throws Exception {
        Instant now = Instant.parse("2026-10-22T00:00:00Z");
        ReorderSuggestion s = ReorderSuggestion.reconstitute(
                UUID.randomUUID(), "SKU-001", UUID.randomUUID(), "WH-SEOUL-01", "WMS_WAREHOUSE",
                "SUP-0043", 100, SuggestionStatus.MATERIALIZED, SuggestionSource.ALERT,
                UUID.randomUUID(), 5, 10, UUID.randomUUID(), "scm", 2, now, now);

        JsonNode row = firstRow(s);

        assertThat(keys(row)).isEqualTo(CONTRACT_FIELDS);
        assertThat(row.path("reorderPointAtTrigger").isInt()).isTrue();
        assertThat(row.path("reorderPointAtTrigger").asInt()).isEqualTo(10);
        assertThat(row.path("triggerAvailableQty").asInt()).isEqualTo(5);
    }

    @Test
    @WithMockUser(roles = "SCM_DEMAND_PLANNING_READ")
    @DisplayName("최소 행 = null 값 키가 하나도 없다 (값 없음 = 키 없음)")
    void minimalRow_carriesNoNullValuedKey() throws Exception {
        // A BATCH suggestion raised without a policy: no warehouse code, no snapshot, not
        // materialized — every nullable field is null.
        ReorderSuggestion s = ReorderSuggestion.raiseFromBatch(
                UUID.randomUUID(), "SKU-001", UUID.randomUUID(), null, null,
                "SUP-0043", 25, 3, null, "scm", Instant.now());

        JsonNode row = firstRow(s);

        List<String> nullValued = new ArrayList<>();
        row.fields().forEachRemaining(e -> {
            if (e.getValue().isNull()) nullValued.add(e.getKey());
        });
        assertThat(nullValued).as("NON_NULL: a null value is an absent key, never \"x\": null").isEmpty();
        assertThat(keys(row)).doesNotContain("reorderPointAtTrigger", "warehouseCode", "materializedPoId");
        assertThat(keys(row)).containsAll(ALWAYS_PRESENT);
        assertThat(CONTRACT_FIELDS).containsAll(keys(row));
    }
}
