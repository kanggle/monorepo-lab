package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorConsoleEligibilityQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-MONO-772 S4 (auth-to-admin.md § GET /internal/operators/console-eligibility) — the read auth-service's
 * issuer makes, fail-CLOSED, for every console {@code authorization_code} / {@code refresh_token} issuance to a
 * consumer-pool principal.
 *
 * <p>Under the {@code @Order(0)} {@code /internal/**} resource-server chain (IAM {@code client_credentials} JWT;
 * the test/standalone bypass authenticates slice tests), like {@link OperatorFacetController} and
 * {@link OperatorAssignmentCheckController}. Its own controller rather than a method on the facet controller so
 * the two questions — «may this account be moved» vs «may this account enter the console» — stay visibly apart.
 * Read-only — no {@code admin_actions} row.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/operators")
public class OperatorConsoleEligibilityController {

    private final OperatorConsoleEligibilityQueryUseCase eligibilityQueryUseCase;

    /**
     * {@code GET /internal/operators/console-eligibility?accountId=} → always {@code 200 {"eligible": bool}}
     * («no» is an answer, not an error). Missing / blank {@code accountId} → {@code 400 VALIDATION_ERROR}.
     */
    @GetMapping("/console-eligibility")
    public ResponseEntity<ConsoleEligibilityResponse> consoleEligibility(@RequestParam String accountId) {
        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
        return ResponseEntity.ok(new ConsoleEligibilityResponse(
                eligibilityQueryUseCase.isConsoleEligible(accountId.trim())));
    }

    /** {@code {"eligible": true|false}}. */
    public record ConsoleEligibilityResponse(boolean eligible) {}
}
