package com.example.scmplatform.demandplanning.adapter.inbound.web.controller;

import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.ApiEnvelope;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.DismissRequest;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.PageMeta;
import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.SuggestionResponse;
import com.example.scmplatform.demandplanning.application.usecase.SuggestionQueryUseCase;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for reorder suggestion operations.
 * Base path: /api/demand-planning/suggestions (mapped at /api/v1/demand-planning/** via gateway).
 */
@RestController
@RequestMapping("/api/demand-planning/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    static final String TENANT_ID = "scm";

    private final SuggestionQueryUseCase suggestionQueryUseCase;

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<SuggestionResponse>>> listSuggestions(
            @RequestParam(required = false) SuggestionStatus status,
            @RequestParam(required = false) String skuCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ReorderSuggestion> result = suggestionQueryUseCase.listSuggestions(
                TENANT_ID, status, skuCode, PageRequest.of(page, Math.min(size, 100)));

        List<SuggestionResponse> data = result.getContent().stream()
                .map(SuggestionResponse::from).toList();
        PageMeta meta = new PageMeta(result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiEnvelope.of(data, meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiEnvelope<SuggestionResponse>> getSuggestion(@PathVariable UUID id) {
        ReorderSuggestion suggestion = suggestionQueryUseCase.getById(id);
        return ResponseEntity.ok(ApiEnvelope.of(SuggestionResponse.from(suggestion)));
    }

    /**
     * POST /suggestions/{id}/approve — BE-025 (procurement materialization).
     * Returns 501 Not Implemented in this task scope.
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiEnvelope<Map<String, String>>> approve(@PathVariable UUID id,
                                                                     @RequestBody(required = false) Object body) {
        return ResponseEntity.status(501)
                .body(ApiEnvelope.of(Map.of("code", "NOT_IMPLEMENTED",
                        "message", "approve endpoint is implemented in BE-025")));
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<ApiEnvelope<SuggestionResponse>> dismiss(
            @PathVariable UUID id,
            @RequestBody(required = false) DismissRequest request) {
        String reason = request != null ? request.reason() : null;
        ReorderSuggestion dismissed = suggestionQueryUseCase.dismiss(id, reason);
        return ResponseEntity.ok(ApiEnvelope.of(SuggestionResponse.from(dismissed)));
    }
}
