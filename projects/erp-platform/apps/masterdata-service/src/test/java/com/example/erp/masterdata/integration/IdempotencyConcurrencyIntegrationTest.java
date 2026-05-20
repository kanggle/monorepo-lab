package com.example.erp.masterdata.integration;

import com.example.erp.masterdata.application.port.outbound.IdempotencyStore;
import com.example.erp.masterdata.presentation.dto.ApiEnvelope;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT for FIN-BE-004 final-form idempotency (architecture.md § Idempotency).
 * Drives the production {@link IdempotentExecution} wrapper directly:
 * 8 concurrent threads with the same Idempotency-Key + identical payload
 * MUST execute the action <b>exactly once</b>. Assertion is
 * {@code actionExecuted == 1} EXACT — never weakened.
 */
class IdempotencyConcurrencyIntegrationTest extends AbstractMasterdataIntegrationTest {

    @Autowired
    IdempotentExecution idempotentExecution;
    @Autowired
    IdempotencyStore idempotencyStore;

    @Test
    @DisplayName("F-equivalent (FIN-BE-004): same key + identical payload concurrently → action runs exactly once")
    void concurrentSameKeyExactlyOnce() throws Exception {
        String endpoint = "POST /api/erp/masterdata/test";
        String key = "idem-erp-concurrent-1";
        Map<String, Object> payload = Map.of("name", "Sales", "code", "DEPT-CONC-1");

        AtomicInteger actionExecuted = new AtomicInteger();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Callable<ResponseEntity<?>> task = () -> idempotentExecution.run(
                TENANT_ERP, endpoint, key, payload, () -> {
                    actionExecuted.incrementAndGet();
                    return ResponseEntity.status(201)
                            .body(ApiEnvelope.of(Map.of("created", "DEPT-CONC-1")));
                });

        List<Future<ResponseEntity<?>>> futures = pool.invokeAll(
                Collections.nCopies(threads, task));
        pool.shutdown();

        // Exactly one thread performed the action (the genuine F1 guarantee).
        // STRENGTHENED ASSERTION — DO NOT weaken to >= 1 (FIN-BE-004 discipline).
        assertThat(actionExecuted.get()).isEqualTo(1);
        for (Future<ResponseEntity<?>> f : futures) {
            assertThat(f.get().getStatusCode().is2xxSuccessful()).isTrue();
        }
    }

    @Test
    @DisplayName("Idempotency: same key + DIFFERENT payload → CONFLICT; identical → REPLAY")
    void differentPayloadConflictAndReplay() {
        String endpoint = "POST /api/erp/masterdata/conflict-test";
        String key = "idem-conflict-1";

        IdempotencyStore.Claim won = idempotencyStore.claim(
                TENANT_ERP, endpoint, key, "hash-A");
        assertThat(won.outcome()).isEqualTo(IdempotencyStore.Claim.Outcome.EXECUTE);
        idempotencyStore.complete(TENANT_ERP, endpoint, key, "hash-A",
                new IdempotencyStore.StoredResponse(201, "{\"a\":1}"));

        IdempotencyStore.Claim same = idempotencyStore.claim(
                TENANT_ERP, endpoint, key, "hash-A");
        IdempotencyStore.Claim diff = idempotencyStore.claim(
                TENANT_ERP, endpoint, key, "hash-B");

        assertThat(same.outcome()).isEqualTo(IdempotencyStore.Claim.Outcome.REPLAY);
        assertThat(same.storedResponse().status()).isEqualTo(201);
        assertThat(diff.outcome()).isEqualTo(IdempotencyStore.Claim.Outcome.CONFLICT);
    }
}
