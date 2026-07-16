package com.example.scmplatform.procurement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.scmplatform.procurement.application.port.outbound.IdempotencyStore;
import com.example.scmplatform.procurement.domain.error.IdempotencyKeyMismatchException;
import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fast-lane unit coverage for {@link IdempotencyExecutor} (TASK-BE-445) — the
 * service-layer wrapper that finally enforces the {@code Idempotency-Key} the
 * procurement PO mutation endpoints declared but discarded. Runs in the default
 * {@code test} task (no Docker), independent of the flaky Testcontainers
 * {@code Integration (scm-platform)} lane.
 *
 * <p>Uses an in-memory {@link IdempotencyStore} fake with the real PK semantics
 * (a second insert for the same key throws, like the DB PK) and a real
 * {@link ObjectMapper} so the {@link com.example.scmplatform.procurement.application.PurchaseOrderView}
 * response actually round-trips through the cache.
 */
class IdempotencyExecutorTest {

    private static final String TENANT = "scm";
    private static final String ENDPOINT = "POST /api/procurement/po";

    private InMemoryStore store;
    private IdempotencyExecutor executor;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        executor = new IdempotencyExecutor(store, new ObjectMapper().findAndRegisterModules());
    }

    private static PurchaseOrderView sampleView(String id) {
        return new PurchaseOrderView(
                id, TENANT, "PO-ABCD1234", "sup-1", "buyer-1",
                PoStatus.DRAFT, PoOrigin.OPERATOR, null,
                new BigDecimal("100.00"), "USD",
                null, null, null, null, Instant.parse("2026-07-17T00:00:00Z"),
                Instant.parse("2026-07-17T00:00:00Z"),
                List.of(new PurchaseOrderView.LineView(
                        "line-1", 1, "SKU-1", "SUP-SKU-1",
                        new BigDecimal("2"), new BigDecimal("50.00"), BigDecimal.ZERO)));
    }

    @Test
    @DisplayName("first request runs the action once and caches the response")
    void firstRequest_runsOnce_caches() {
        AtomicInteger runs = new AtomicInteger();
        PurchaseOrderView view = executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201,
                PurchaseOrderView.class, () -> {
                    runs.incrementAndGet();
                    return sampleView("po-1");
                });

        assertThat(runs.get()).isEqualTo(1);
        assertThat(view.id()).isEqualTo("po-1");
        assertThat(store.find(TENANT, ENDPOINT, "k1")).isPresent();
    }

    @Test
    @DisplayName("replay (same key, same payload hash) returns the cached response and does NOT re-run the action")
    void replay_sameHash_notReExecuted() {
        executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201, PurchaseOrderView.class,
                () -> sampleView("po-1"));

        AtomicInteger secondRuns = new AtomicInteger();
        PurchaseOrderView replay = executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201,
                PurchaseOrderView.class, () -> {
                    secondRuns.incrementAndGet();
                    return sampleView("po-DIFFERENT");
                });

        assertThat(secondRuns.get())
                .as("the mutation must NOT run again on replay")
                .isZero();
        assertThat(replay.id())
                .as("the cached PO id is replayed, not a fresh one")
                .isEqualTo("po-1");
    }

    @Test
    @DisplayName("same key + different payload hash → 422 IDEMPOTENCY_KEY_MISMATCH, action not run")
    void differentHash_throwsMismatch() {
        executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201, PurchaseOrderView.class,
                () -> sampleView("po-1"));

        AtomicInteger runs = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(TENANT, ENDPOINT, "k1", "hashB", 201,
                PurchaseOrderView.class, () -> {
                    runs.incrementAndGet();
                    return sampleView("po-2");
                }))
                .isInstanceOf(IdempotencyKeyMismatchException.class);
        assertThat(runs.get()).isZero();
    }

    @Test
    @DisplayName("different keys execute independently")
    void differentKeys_independent() {
        AtomicInteger runs = new AtomicInteger();
        executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201, PurchaseOrderView.class,
                () -> { runs.incrementAndGet(); return sampleView("po-1"); });
        executor.execute(TENANT, ENDPOINT, "k2", "hashA", 201, PurchaseOrderView.class,
                () -> { runs.incrementAndGet(); return sampleView("po-2"); });
        assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("PurchaseOrderView round-trips through the cache (records + Instant + enums + BigDecimal)")
    void view_roundTrips() {
        PurchaseOrderView original = sampleView("po-rt");
        executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201, PurchaseOrderView.class, () -> original);

        PurchaseOrderView replay = executor.execute(TENANT, ENDPOINT, "k1", "hashA", 201,
                PurchaseOrderView.class, () -> { throw new AssertionError("must not run"); });

        assertThat(replay.status()).isEqualTo(PoStatus.DRAFT);
        assertThat(replay.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(replay.lines()).hasSize(1);
        assertThat(replay.lines().get(0).sku()).isEqualTo("SKU-1");
        assertThat(replay.updatedAt()).isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
    }

    /** In-memory {@link IdempotencyStore} with DB-PK semantics: a duplicate insert throws. */
    private static final class InMemoryStore implements IdempotencyStore {
        private final Map<String, IdempotencyRecord> rows = new HashMap<>();

        private static String key(String t, String e, String k) {
            return t + "|" + e + "|" + k;
        }

        @Override
        public Optional<IdempotencyRecord> find(String tenantId, String endpoint, String key) {
            return Optional.ofNullable(rows.get(key(tenantId, endpoint, key)));
        }

        @Override
        public void save(String tenantId, String endpoint, String key,
                         String payloadHash, int responseStatus, String responseBody, Duration ttl) {
            String k = key(tenantId, endpoint, key);
            if (rows.containsKey(k)) {
                throw new org.springframework.dao.DataIntegrityViolationException("duplicate idempotency key");
            }
            rows.put(k, new IdempotencyRecord(payloadHash, responseStatus, responseBody));
        }
    }
}
