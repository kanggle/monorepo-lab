package com.example.auth.presentation;

import com.example.auth.application.MoveCredentialToConsumerPoolUseCase;
import com.example.auth.application.MoveCredentialToConsumerPoolUseCase.Outcome;
import com.example.auth.presentation.dto.ConsumerPoolMoveRequest;
import com.example.auth.presentation.dto.ConsumerPoolMoveResponse;
import com.example.web.dto.ErrorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-618 (auth-internal.md § POST /internal/auth/consumer-pool/moves) — the auth_db half of the
 * consumer-pool legacy move. account-service calls it as the last step of one account's transaction.
 *
 * <p>Under {@code /internal/auth/**} — GAP {@code client_credentials} JWT with {@code internal.invoke}
 * (the test/standalone bypass authenticates tests). Never exposed through the public gateway (S2).
 *
 * <p>Every refusal is a {@code 409} with its own code (account-service turns it into a skip reason); an
 * admin-service that cannot answer the operator-facet question is {@code 503} (fail-closed).
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalConsumerPoolMoveController {

    private final MoveCredentialToConsumerPoolUseCase moveUseCase;

    @PostMapping("/consumer-pool/moves")
    public ResponseEntity<?> move(@Valid @RequestBody ConsumerPoolMoveRequest request) {
        Outcome outcome = moveUseCase.execute(request.accountId(), request.siteTenantId());
        return switch (outcome) {
            case MOVED -> ResponseEntity.ok(new ConsumerPoolMoveResponse(true, false));
            case ALREADY_IN_POOL -> ResponseEntity.ok(new ConsumerPoolMoveResponse(false, true));
            case NO_CREDENTIAL -> ResponseEntity.ok(new ConsumerPoolMoveResponse(false, false));
            case CREDENTIAL_TENANT_MISMATCH -> conflict(ErrorResponse.of("POOL_MOVE_CREDENTIAL_TENANT_MISMATCH",
                    "The account's credential is in neither the pool nor the named site"));
            case SOCIAL_LINKED -> conflict(ErrorResponse.of("POOL_MOVE_SOCIAL_LINKED",
                    "The account has linked social identities and is not moved in this step"));
            case POOL_CREDENTIAL_EXISTS -> conflict(ErrorResponse.of("POOL_MOVE_CREDENTIAL_EXISTS",
                    "A pool credential with the same email already exists"));
            case OPERATOR_FACETED -> conflict(ErrorResponse.of("POOL_MOVE_OPERATOR_FACETED",
                    "The account carries an operator facet and is not moved in this step"));
            case FACET_UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse.of("SERVICE_UNAVAILABLE",
                            "Operator-facet check is unavailable; nothing was moved"));
        };
    }

    private static ResponseEntity<ErrorResponse> conflict(ErrorResponse body) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
