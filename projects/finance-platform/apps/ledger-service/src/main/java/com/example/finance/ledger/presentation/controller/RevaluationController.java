package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase;
import com.example.finance.ledger.application.RevalueForeignBalanceUseCase.Result;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.RevaluationRequest;
import com.example.finance.ledger.presentation.dto.RevaluationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FX revaluation REST endpoint (ledger-api.md § 10, 9th increment — TASK-FIN-BE-015).
 * An operator revalues a foreign-currency position at a closing (spot) rate, booking
 * the base-carrying delta to {@code FX_GAIN}/{@code FX_LOSS}. Carries NO
 * {@code @Transactional} — the use case owns the single guarded write boundary
 * ({@link RevalueForeignBalanceUseCase} funnels through {@code PostJournalEntryUseCase.post});
 * the controller never touches JPA repositories directly (architecture.md § boundary
 * rules, F1).
 *
 * <p>A booked revaluation returns {@code 201} ({@code revalued:true}); a no-op (no
 * position / already at spot) or an idempotent replay returns {@code 200}
 * ({@code revalued:false}). {@code .authenticated()} + the dual-accept tenant gate
 * (parity with manual posting — no new scope-authority axis).
 */
@RestController
@RequestMapping("/api/finance/ledger/revaluations")
@RequiredArgsConstructor
public class RevaluationController {

    private final RevalueForeignBalanceUseCase revalueForeignBalance;

    @PostMapping
    public ResponseEntity<ApiEnvelope<RevaluationResponse>> revalue(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RevaluationRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        Result result = revalueForeignBalance.revalue(
                request.toCommand(actor.tenantId(), actor.identity(), idempotencyKey));
        RevaluationResponse body = RevaluationResponse.from(result);
        HttpStatus status = result.revalued() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiEnvelope.of(body));
    }

}
