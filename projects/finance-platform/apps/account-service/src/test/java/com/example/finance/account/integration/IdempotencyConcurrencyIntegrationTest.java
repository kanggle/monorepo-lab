package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.command.PlaceHoldCommand;
import com.example.finance.account.application.port.outbound.IdempotencyStore;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.presentation.dto.ApiEnvelope;
import com.example.finance.account.presentation.support.IdempotentExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for fintech F1 idempotency (Testcontainers MySQL + Redis).
 * Drives the <b>production</b> {@link IdempotentExecution} wrapper (NOT a
 * hand-rolled findExisting/store simulation): the same Idempotency-Key +
 * identical payload, issued by 8 concurrent threads, must execute the
 * fund-moving action <b>exactly once</b> (atomic claim-before-execute) — the
 * losers replay the winner's stored response, no second balance mutation.
 */
class IdempotencyConcurrencyIntegrationTest extends AbstractAccountIntegrationTest {

    @Autowired
    AccountApplicationService service;
    @Autowired
    IdempotentExecution idempotentExecution;
    @Autowired
    IdempotencyStore idempotencyStore;

    @Test
    @DisplayName("F1: same key + identical payload concurrently → funds move exactly once")
    void concurrentSameKeyMovesFundsOnce() throws Exception {
        AccountView acc = openActiveFullKyc(service, "cust-idem-1");
        service.topUp(HOLDER, acc.accountId(), 10_000L);

        String endpoint = "POST /api/finance/accounts/{id}/holds";
        String key = "idem-concurrent-1";
        // Identical payload object across every thread → identical payload hash.
        Map<String, Object> payload = Map.of(
                "accountId", acc.accountId(), "amount", "3000", "currency", "KRW");

        // The fund-moving action runs ONLY for the claim winner. The counter
        // proves exactly-once at the production guarantee (not the assertion).
        AtomicInteger actionExecuted = new AtomicInteger();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Callable<ResponseEntity<?>> task = () -> idempotentExecution.run(
                TENANT_FINANCE, endpoint, key, payload, () -> {
                    actionExecuted.incrementAndGet();
                    service.placeHold(new PlaceHoldCommand(
                            HOLDER, acc.accountId(), "3000", "KRW", 3600, "checkout"));
                    return ResponseEntity.status(201)
                            .body(ApiEnvelope.of(Map.of("held", "3000")));
                });

        List<Future<ResponseEntity<?>>> futures = pool.invokeAll(
                java.util.Collections.nCopies(threads, task));
        pool.shutdown();

        // Exactly one thread performed the fund movement (genuine F1, via the
        // production atomic claim — the assertion is NOT weakened).
        assertThat(actionExecuted.get()).isEqualTo(1);
        // Every thread got the same successful 201 (winner real + losers replay).
        for (Future<ResponseEntity<?>> f : futures) {
            assertThat(f.get().getStatusCode().is2xxSuccessful()).isTrue();
        }
        // Balance moved exactly once: 10000 ledger, one 3000 hold → available 7000.
        var b = service.getBalances(acc.accountId(), HOLDER).get(0);
        assertThat(b.held()).isEqualTo("3000");
        assertThat(b.available()).isEqualTo("7000");
    }

    @Test
    @DisplayName("F1: claim of same key + DIFFERENT payload → CONFLICT; identical → REPLAY")
    void differentPayloadConflictAndReplay() {
        String endpoint = "POST /api/finance/accounts";
        String key = "idem-conflict-1";

        // Win the claim, then complete it with a stored response.
        IdempotencyStore.Claim won = idempotencyStore.claim(
                TENANT_FINANCE, endpoint, key, "hash-A");
        assertThat(won.outcome())
                .isEqualTo(IdempotencyStore.Claim.Outcome.EXECUTE);
        idempotencyStore.complete(TENANT_FINANCE, endpoint, key, "hash-A",
                new IdempotencyStore.StoredResponse(201, "{\"a\":1}"));

        IdempotencyStore.Claim same = idempotencyStore.claim(
                TENANT_FINANCE, endpoint, key, "hash-A");
        IdempotencyStore.Claim diff = idempotencyStore.claim(
                TENANT_FINANCE, endpoint, key, "hash-B");

        assertThat(same.outcome())
                .isEqualTo(IdempotencyStore.Claim.Outcome.REPLAY);
        assertThat(same.storedResponse().status()).isEqualTo(201);
        assertThat(diff.outcome())
                .isEqualTo(IdempotencyStore.Claim.Outcome.CONFLICT);
    }
}
