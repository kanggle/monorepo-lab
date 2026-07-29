package com.example.finance.account.presentation.support;

import com.example.finance.account.application.port.outbound.IdempotencyStore;
import com.example.finance.account.domain.error.DomainErrors.IdempotencyKeyConflictException;
import com.example.finance.account.presentation.dto.ApiEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IdempotentExecution} — in particular TASK-FIN-BE-063:
 * the request-body hash fed to {@link IdempotencyStore#claim} MUST be
 * key-order-canonical, or a same-key replay of a semantically-identical body
 * (JSON keys serialized in a different order) would incorrectly 409-conflict
 * instead of replaying the cached response.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class IdempotentExecutionTest {

    private static final String TENANT = "finance";
    private static final String ENDPOINT = "POST /api/finance/accounts/{id}/holds";
    private static final String KEY = "idem-key-1";

    @Mock
    private IdempotencyStore idempotencyStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Supplier<ResponseEntity<?>> action(int status, Object body) {
        return () -> ResponseEntity.status(status).body(body);
    }

    /** (a) Same JSON keys, different insertion order → identical payload hash. */
    @Test
    @DisplayName("TASK-FIN-BE-063 (a): key-reordered but semantically-identical body hashes identically")
    void sameKeysDifferentOrderHashIdentically() {
        IdempotentExecution execution = new IdempotentExecution(idempotencyStore, objectMapper);

        Map<String, Object> orderA = new LinkedHashMap<>();
        orderA.put("amount", "3000");
        orderA.put("currency", "KRW");

        Map<String, Object> orderB = new LinkedHashMap<>();
        orderB.put("currency", "KRW");
        orderB.put("amount", "3000");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        when(idempotencyStore.claim(eq(TENANT), eq(ENDPOINT), anyString(), hashCaptor.capture()))
                .thenReturn(IdempotencyStore.Claim.execute());

        execution.run(TENANT, ENDPOINT, "key-a", orderA, action(201, Map.of("ok", true)));
        execution.run(TENANT, ENDPOINT, "key-b", orderB, action(201, Map.of("ok", true)));

        assertThat(hashCaptor.getAllValues()).hasSize(2);
        assertThat(hashCaptor.getAllValues().get(0))
                .as("re-ordered keys of the SAME logical body must hash identically")
                .isEqualTo(hashCaptor.getAllValues().get(1));
    }

    /** (b) A genuinely different body must still hash differently. */
    @Test
    @DisplayName("TASK-FIN-BE-063 (b): a genuinely different body still hashes differently")
    void differentBodyHashesDifferently() {
        IdempotentExecution execution = new IdempotentExecution(idempotencyStore, objectMapper);

        Map<String, Object> original = Map.of("amount", "3000", "currency", "KRW");
        Map<String, Object> changed = Map.of("amount", "4000", "currency", "KRW");

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        when(idempotencyStore.claim(eq(TENANT), eq(ENDPOINT), anyString(), hashCaptor.capture()))
                .thenReturn(IdempotencyStore.Claim.execute());

        execution.run(TENANT, ENDPOINT, "key-c", original, action(201, Map.of("ok", true)));
        execution.run(TENANT, ENDPOINT, "key-d", changed, action(201, Map.of("ok", true)));

        assertThat(hashCaptor.getAllValues()).hasSize(2);
        assertThat(hashCaptor.getAllValues().get(0))
                .as("genuinely different payloads must NOT collide")
                .isNotEqualTo(hashCaptor.getAllValues().get(1));
    }

    /** (c) EXECUTE outcome: action runs once, 2xx response is stored via complete(). */
    @Test
    @DisplayName("EXECUTE: action runs, 2xx response stored via complete()")
    void executeStoresSuccessResponse() {
        IdempotentExecution execution = new IdempotentExecution(idempotencyStore, objectMapper);
        when(idempotencyStore.claim(eq(TENANT), eq(ENDPOINT), eq(KEY), anyString()))
                .thenReturn(IdempotencyStore.Claim.execute());

        ResponseEntity<?> response = execution.run(TENANT, ENDPOINT, KEY,
                Map.of("amount", "3000"), action(201, ApiEnvelope.of(Map.of("held", "3000"))));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(idempotencyStore).complete(eq(TENANT), eq(ENDPOINT), eq(KEY),
                anyString(), any(IdempotencyStore.StoredResponse.class));
        verify(idempotencyStore, never()).release(anyString(), anyString(), anyString());
    }

    /** (c) REPLAY outcome: winner's stored response is returned, action NOT re-invoked. */
    @Test
    @DisplayName("REPLAY: same key + identical payload replays the stored response (no re-execution)")
    void replayReturnsStoredResponseWithoutReexecution() {
        IdempotentExecution execution = new IdempotentExecution(idempotencyStore, objectMapper);
        IdempotencyStore.StoredResponse stored =
                new IdempotencyStore.StoredResponse(201, "{\"data\":{\"held\":\"3000\"}}");
        when(idempotencyStore.claim(eq(TENANT), eq(ENDPOINT), eq(KEY), anyString()))
                .thenReturn(IdempotencyStore.Claim.replay(stored));

        AtomicInteger executed = new AtomicInteger();
        ResponseEntity<?> response = execution.run(TENANT, ENDPOINT, KEY,
                Map.of("amount", "3000"), () -> {
                    executed.incrementAndGet();
                    return ResponseEntity.status(201).body(Map.of("held", "3000"));
                });

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(executed.get()).as("REPLAY must NOT re-run the fund-moving action").isZero();
    }

    /** (c) CONFLICT outcome: same key + genuinely different payload → 409 (IdempotencyKeyConflictException). */
    @Test
    @DisplayName("CONFLICT: same key + genuinely different payload → IdempotencyKeyConflictException")
    void conflictOutcomeThrows() {
        IdempotentExecution execution = new IdempotentExecution(idempotencyStore, objectMapper);
        when(idempotencyStore.claim(eq(TENANT), eq(ENDPOINT), eq(KEY), anyString()))
                .thenReturn(IdempotencyStore.Claim.conflict());

        assertThatThrownBy(() -> execution.run(TENANT, ENDPOINT, KEY,
                Map.of("amount", "9999"), action(201, Map.of("ok", true))))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }
}
