package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.UpgradeKycCommand;
import com.example.finance.account.application.port.outbound.IdempotencyStore;
import com.example.finance.account.application.view.AccountView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for fintech F1 idempotency (Testcontainers MySQL + Redis).
 * Proves: same Idempotency-Key + identical payload, issued concurrently,
 * causes the fund movement to occur EXACTLY ONCE (replay returns the first
 * stored response, no second balance mutation).
 */
class IdempotencyConcurrencyIntegrationTest extends AbstractAccountIntegrationTest {

    private static final ActorContext HOLDER =
            new ActorContext("user-1", TENANT_FINANCE, Set.of());
    private static final ActorContext OPERATOR =
            new ActorContext("op-1", TENANT_FINANCE, Set.of("OPERATOR"));

    @Autowired
    AccountApplicationService service;
    @Autowired
    IdempotencyStore idempotencyStore;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("F1: same key + identical payload concurrently → funds move exactly once")
    void concurrentSameKeyMovesFundsOnce() throws Exception {
        AccountView opened = service.openAccount(new OpenAccountCommand(
                HOLDER, "cust-idem-1", "KRW", "NONE"));
        AccountView acc = service.upgradeKyc(new UpgradeKycCommand(
                OPERATOR, opened.accountId(), "FULL", "kyc"));
        service.topUp(HOLDER, acc.accountId(), 10_000L);

        String endpoint = "POST /api/finance/accounts/{id}/holds";
        String key = "idem-concurrent-1";
        String payloadHash = "hash-fixed";
        String storedBody = objectMapper.writeValueAsString(
                java.util.Map.of("holdId", "first-only"));

        // Simulate the controller wrapper's behaviour under contention: the
        // first writer stores the response; replays return it verbatim. The
        // fund-moving service call is only invoked when the store reports MISS.
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Callable<Boolean> task = () -> {
            IdempotencyStore.Lookup lk = idempotencyStore.findExisting(
                    TENANT_FINANCE, endpoint, key, payloadHash);
            if (lk.outcome() == IdempotencyStore.Lookup.Outcome.MISS) {
                try {
                    service.placeHold(new com.example.finance.account.application
                            .command.PlaceHoldCommand(HOLDER, acc.accountId(),
                            "3000", "KRW", 3600, "checkout"));
                    idempotencyStore.store(TENANT_FINANCE, endpoint, key,
                            payloadHash, new IdempotencyStore.StoredResponse(
                                    201, storedBody));
                    return true; // this thread performed the movement
                } catch (RuntimeException dup) {
                    return false; // lost the race; another thread moved funds
                }
            }
            return false;
        };
        List<Future<Boolean>> futures = pool.invokeAll(
                java.util.Collections.nCopies(threads, task));
        pool.shutdown();

        long performed = 0;
        for (Future<Boolean> f : futures) if (Boolean.TRUE.equals(f.get())) performed++;

        // Fund movement happened at most once (often exactly once); the held
        // amount is never doubled. With 10000 ledger and a 3000 hold, available
        // must be 7000 — NOT 4000 (which would prove a double movement).
        var b = service.getBalances(acc.accountId(), HOLDER).get(0);
        assertThat(performed).isLessThanOrEqualTo(1);
        assertThat(b.held()).isEqualTo("3000");
        assertThat(b.available()).isEqualTo("7000");
    }

    @Test
    @DisplayName("F1: same key + DIFFERENT payload → CONFLICT signalled by the store")
    void differentPayloadConflict() {
        String endpoint = "POST /api/finance/accounts";
        String key = "idem-conflict-1";
        idempotencyStore.store(TENANT_FINANCE, endpoint, key, "hash-A",
                new IdempotencyStore.StoredResponse(201, "{\"a\":1}"));

        IdempotencyStore.Lookup same = idempotencyStore.findExisting(
                TENANT_FINANCE, endpoint, key, "hash-A");
        IdempotencyStore.Lookup diff = idempotencyStore.findExisting(
                TENANT_FINANCE, endpoint, key, "hash-B");

        assertThat(same.outcome()).isEqualTo(IdempotencyStore.Lookup.Outcome.REPLAY);
        assertThat(diff.outcome()).isEqualTo(IdempotencyStore.Lookup.Outcome.CONFLICT);
    }
}
