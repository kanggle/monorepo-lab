package com.example.finance.ledger.presentation.controller;

import com.example.finance.ledger.application.ActorContext;
import com.example.finance.ledger.application.CloseAccountingPeriodUseCase;
import com.example.finance.ledger.application.OpenAccountingPeriodUseCase;
import com.example.finance.ledger.application.QueryAccountingPeriodUseCase;
import com.example.finance.ledger.application.view.AccountingPeriodView;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.infrastructure.security.ActorContextResolver;
import com.example.finance.ledger.presentation.dto.ApiEnvelope;
import com.example.finance.ledger.presentation.dto.OpenPeriodRequest;
import com.example.finance.ledger.presentation.dto.PeriodDetailResponse;
import com.example.finance.ledger.presentation.dto.PeriodResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accounting-period REST endpoints (ledger-api.md § Accounting periods, 2nd
 * increment). The increment's only write endpoints (open/close) —
 * {@code .authenticated()} + the dual-accept tenant gate (no new scope-authority
 * axis; the operator caller arrives via the platform-console client). Carries NO
 * {@code @Transactional} — the use cases own the boundary; it never touches JPA
 * repositories directly (architecture.md § boundary rules).
 */
@RestController
@RequestMapping("/api/finance/ledger/periods")
@RequiredArgsConstructor
public class PeriodController {

    private final OpenAccountingPeriodUseCase openPeriod;
    private final CloseAccountingPeriodUseCase closePeriod;
    private final QueryAccountingPeriodUseCase queryPeriod;

    @PostMapping
    public ResponseEntity<ApiEnvelope<PeriodResponse>> open(
            @RequestBody OpenPeriodRequest request) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AccountingPeriod period = openPeriod.open(
                actor.tenantId(), request.from(), request.to(), actorIdentity(actor));
        PeriodResponse body = PeriodResponse.from(AccountingPeriodView.summary(period));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(body));
    }

    @PostMapping("/{periodId}/close")
    public ResponseEntity<ApiEnvelope<PeriodDetailResponse>> close(
            @PathVariable String periodId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AccountingPeriodView view = closePeriod.close(
                periodId, actor.tenantId(), actorIdentity(actor));
        return ResponseEntity.ok(ApiEnvelope.of(PeriodDetailResponse.from(view)));
    }

    @GetMapping
    public ResponseEntity<ApiEnvelope<List<PeriodResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        List<AccountingPeriodView> all = queryPeriod.listPeriods(actor.tenantId());
        List<PeriodResponse> pageContent = paginate(all, page, size).stream()
                .map(PeriodResponse::from).toList();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("page", page);
        meta.put("size", size);
        meta.put("totalElements", (long) all.size());
        meta.put("totalPages", totalPages(all.size(), size));
        return ResponseEntity.ok(ApiEnvelope.of(pageContent, meta));
    }

    @GetMapping("/{periodId}")
    public ResponseEntity<ApiEnvelope<PeriodDetailResponse>> get(
            @PathVariable String periodId) {
        ActorContext actor = ActorContextResolver.currentOrThrow();
        AccountingPeriodView view = queryPeriod.getPeriod(periodId, actor.tenantId());
        return ResponseEntity.ok(ApiEnvelope.of(PeriodDetailResponse.from(view)));
    }

    /** The actor identity recorded as {@code closedBy} — the JWT subject, else the tenant. */
    private static String actorIdentity(ActorContext actor) {
        return actor.subject() != null ? actor.subject() : actor.tenantId();
    }

    private static List<AccountingPeriodView> paginate(List<AccountingPeriodView> all,
                                                       int page, int size) {
        if (size <= 0 || all.isEmpty()) {
            return List.of();
        }
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    private static int totalPages(int total, int size) {
        if (size <= 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
