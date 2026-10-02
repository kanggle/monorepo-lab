package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorFacetQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-618 (auth-to-admin.md § GET /internal/operators/facet) — the operator-facet read auth-service
 * makes, fail-CLOSED, before it moves a single-site consumer account's credential into the consumer pool.
 *
 * <p>Under the {@code @Order(0)} {@code /internal/**} resource-server chain (GAP {@code client_credentials}
 * JWT with {@code internal.invoke}; the test/standalone bypass authenticates slice tests), like
 * {@link OperatorAssignmentCheckController}. Read-only — no {@code admin_actions} row.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/operators")
public class OperatorFacetController {

    private final OperatorFacetQueryUseCase facetQueryUseCase;

    /**
     * {@code GET /internal/operators/facet?accountId=&identityId=} → {@code {"operatorFaceted": bool}}.
     * {@code identityId} is optional; blank is treated as absent.
     */
    @GetMapping("/facet")
    public ResponseEntity<OperatorFacetResponse> facet(
            @RequestParam String accountId,
            @RequestParam(required = false) String identityId) {
        return ResponseEntity.ok(new OperatorFacetResponse(
                facetQueryUseCase.isOperatorFaceted(accountId, identityId)));
    }

    /** {@code {"operatorFaceted": true|false}}. */
    public record OperatorFacetResponse(boolean operatorFaceted) {}
}
