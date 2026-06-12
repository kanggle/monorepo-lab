package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.SettleForeignPositionUseCase;
import com.example.finance.ledger.application.SettleForeignPositionUseCase.Result;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.SettlementRequest;
import com.example.finance.ledger.presentation.dto.SettlementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FX settlement REST endpoint (ledger-api.md § 11, 10th increment — TASK-FIN-BE-016).
 * An operator settles a foreign-currency position at a settlement (spot) rate, removing
 * the position at carrying and booking the realized {@code FX_GAIN}/{@code FX_LOSS}.
 * Carries NO {@code @Transactional} — the use case owns the single guarded write
 * boundary ({@link SettleForeignPositionUseCase} funnels through
 * {@code PostJournalEntryUseCase.post}); the controller never touches JPA repositories
 * directly (architecture.md § boundary rules, F1).
 *
 * <p>A booked settlement returns {@code 201} ({@code settled:true}); a no-op (no
 * position) or an idempotent replay returns {@code 200} ({@code settled:false}).
 * {@code .authenticated()} + the dual-accept tenant gate (parity with revaluation /
 * manual posting — no new scope-authority axis).
 */
@RestController
@RequestMapping("/api/finance/ledger/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettleForeignPositionUseCase settleForeignPosition;

    @PostMapping
    public ResponseEntity<ApiEnvelope<SettlementResponse>> settle(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SettlementRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        Result result = settleForeignPosition.settle(
                request.toCommand(actor.tenantId(), actorIdentity(actor), idempotencyKey));
        SettlementResponse body = SettlementResponse.from(result);
        HttpStatus status = result.settled() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiEnvelope.of(body));
    }

    /** The actor identity recorded as the audit actor — the JWT subject, else the tenant. */
    private static String actorIdentity(ActorContext actor) {
        return actor.subject() != null ? actor.subject() : actor.tenantId();
    }
}
