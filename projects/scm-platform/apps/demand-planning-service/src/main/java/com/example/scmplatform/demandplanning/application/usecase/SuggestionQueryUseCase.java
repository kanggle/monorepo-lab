package com.example.scmplatform.demandplanning.application.usecase;

import com.example.scmplatform.demandplanning.application.port.outbound.ReorderSuggestionPort;
import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;
import com.example.scmplatform.demandplanning.domain.error.SuggestionNotFoundException;
import com.example.scmplatform.demandplanning.domain.model.ReorderSuggestion;
import com.example.scmplatform.demandplanning.domain.model.SuggestionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Query and mutation use cases for reorder suggestions exposed via the REST surface.
 * Approve (BE-025) is not implemented in this task — returns a 501 stub.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionQueryUseCase {

    private final ReorderSuggestionPort suggestionPort;

    @Transactional(readOnly = true)
    public Page<ReorderSuggestion> listSuggestions(String tenantId, SuggestionStatus status,
                                                    String skuCode, Pageable pageable) {
        return suggestionPort.findAll(tenantId, status, skuCode, pageable);
    }

    @Transactional(readOnly = true)
    public ReorderSuggestion getById(UUID id) {
        return suggestionPort.findById(id)
                .orElseThrow(() -> new SuggestionNotFoundException(id));
    }

    /**
     * Dismiss a suggestion. Idempotent: re-dismissing an already DISMISSED suggestion is a no-op.
     */
    @Transactional
    public ReorderSuggestion dismiss(UUID id, String reason) {
        ReorderSuggestion suggestion = getById(id);
        if (suggestion.getStatus() == SuggestionStatus.DISMISSED) {
            // Idempotent: already dismissed, return as-is
            return suggestion;
        }
        if (suggestion.getStatus() == SuggestionStatus.MATERIALIZED) {
            throw new InvalidSuggestionStateException(
                    "Cannot dismiss a MATERIALIZED suggestion");
        }
        suggestion.dismiss(Instant.now());
        ReorderSuggestion saved = suggestionPort.save(suggestion);
        log.info("Suggestion dismissed: id={} reason={}", id, reason);
        return saved;
    }
}
