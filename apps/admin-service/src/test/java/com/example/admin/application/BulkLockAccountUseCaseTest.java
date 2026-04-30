package com.example.admin.application;

import com.example.admin.application.exception.BatchSizeExceededException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.port.BulkLockIdempotencyPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class BulkLockAccountUseCaseTest {

    @Mock AccountAdminUseCase accountAdminUseCase;
    @Mock BulkLockIdempotencyPort idempotencyPort;
    @Mock OperatorLookupPort operatorLookupPort;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks BulkLockAccountUseCase useCase;

    private static OperatorContext operator() {
        return new OperatorContext("op-uuid-1", "jti-1");
    }

    private static LockAccountResult okResult(String accountId) {
        return new LockAccountResult(accountId, "ACTIVE", "LOCKED",
                "op-uuid-1", Instant.parse("2026-04-14T00:00:00Z"), "audit-" + accountId);
    }

    private void stubOperatorResolution() {
        when(operatorLookupPort.findInternalId("op-uuid-1")).thenReturn(Optional.of(42L));
    }

    @Test
    void batch_over_100_throws_batch_size_exceeded() {
        List<String> ids = IntStream.range(0, 101).mapToObj(i -> "acc-" + i).collect(Collectors.toList());

        assertThatThrownBy(() -> useCase.execute(new BulkLockAccountCommand(
                ids, "fraud-wave", null, "idemp-1", operator())))
                .isInstanceOf(BatchSizeExceededException.class);

        verify(accountAdminUseCase, never()).lock(any());
    }

    @Test
    void batch_exactly_100_is_accepted() {
        stubOperatorResolution();
        List<String> ids = IntStream.range(0, 100).mapToObj(i -> "acc-" + i).collect(Collectors.toList());
        when(idempotencyPort.find(anyLong(), anyString())).thenReturn(Optional.empty());
        when(accountAdminUseCase.lock(any())).thenAnswer(inv -> {
            LockAccountCommand cmd = inv.getArgument(0);
            return okResult(cmd.accountId());
        });

        BulkLockAccountResult r = useCase.execute(new BulkLockAccountCommand(
                ids, "fraud-wave", null, "idemp-100", operator()));

        assertThat(r.results()).hasSize(100);
        verify(accountAdminUseCase, times(100)).lock(any());
    }

    @Test
    void reason_shorter_than_8_throws_reason_required() {
        assertThatThrownBy(() -> useCase.execute(new BulkLockAccountCommand(
                List.of("acc-1"), "short", null, "idemp", operator())))
                .isInstanceOf(ReasonRequiredException.class);
    }

    @Test
    void duplicate_account_ids_are_deduped_preserving_first_order() {
        stubOperatorResolution();
        when(idempotencyPort.find(anyLong(), anyString())).thenReturn(Optional.empty());
        when(accountAdminUseCase.lock(any())).thenAnswer(inv ->
                okResult(((LockAccountCommand) inv.getArgument(0)).accountId()));

        BulkLockAccountResult r = useCase.execute(new BulkLockAccountCommand(
                List.of("acc-1", "acc-2", "acc-1", "acc-3", "acc-2"),
                "fraud-wave-dedup", null, "idemp-dedup", operator()));

        assertThat(r.results()).extracting(BulkLockAccountResult.Item::accountId)
                .containsExactly("acc-1", "acc-2", "acc-3");
        verify(accountAdminUseCase, times(3)).lock(any());
    }

    @Test
    void per_row_failures_isolated_classified_by_type_fields_not_message() {
        stubOperatorResolution();
        when(idempotencyPort.find(anyLong(), anyString())).thenReturn(Optional.empty());
        when(accountAdminUseCase.lock(any())).thenAnswer(inv -> {
            String id = ((LockAccountCommand) inv.getArgument(0)).accountId();
            return switch (id) {
                case "acc-ok" -> okResult(id);
                // 4xx classification uses httpStatus/errorCode — NOT getMessage() text
                case "acc-404" -> throw new NonRetryableDownstreamException(
                        "downstream-refused", null, 404, "ACCOUNT_NOT_FOUND");
                case "acc-409" -> throw new NonRetryableDownstreamException(
                        "downstream-refused", null, 409, "STATE_TRANSITION_INVALID");
                case "acc-500" -> throw new DownstreamFailureException("boom", null);
                default -> okResult(id);
            };
        });

        BulkLockAccountResult r = useCase.execute(new BulkLockAccountCommand(
                List.of("acc-ok", "acc-404", "acc-409", "acc-500"),
                "partial-failure-test", null, "idemp-partial", operator()));

        assertThat(r.results()).extracting(BulkLockAccountResult.Item::outcome)
                .containsExactly("LOCKED", "NOT_FOUND", "ALREADY_LOCKED", "FAILURE");
        assertThat(r.results().get(1).errorCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(r.results().get(2).errorCode()).isEqualTo("STATE_TRANSITION_INVALID");
        assertThat(r.results().get(3).errorCode()).isEqualTo("DOWNSTREAM_ERROR");
    }

    @Test
    void classification_falls_back_to_http_status_when_error_code_absent() {
        stubOperatorResolution();
        when(idempotencyPort.find(anyLong(), anyString())).thenReturn(Optional.empty());
        when(accountAdminUseCase.lock(any())).thenAnswer(inv -> {
            String id = ((LockAccountCommand) inv.getArgument(0)).accountId();
            return switch (id) {
                case "acc-404-blank" -> throw new NonRetryableDownstreamException(
                        "downstream-refused", null, 404, null);
                case "acc-400-blank" -> throw new NonRetryableDownstreamException(
                        "downstream-refused", null, 400, null);
                default -> okResult(id);
            };
        });

        BulkLockAccountResult r = useCase.execute(new BulkLockAccountCommand(
                List.of("acc-404-blank", "acc-400-blank"),
                "http-status-fallback-reason", null, "idemp-fallback", operator()));

        assertThat(r.results().get(0).outcome()).isEqualTo("NOT_FOUND");
        assertThat(r.results().get(1).outcome()).isEqualTo("ALREADY_LOCKED");
    }

    @Test
    void identical_retry_returns_stored_response_without_reexecuting() throws Exception {
        stubOperatorResolution();

        // Delegate to the use-case's canonical hash method instead of re-deriving it.
        String hash = useCase.computeRequestHash(List.of("acc-a"), "replay-test-reason", null);

        List<BulkLockAccountResult.Item> stored = List.of(
                new BulkLockAccountResult.Item("acc-a", "LOCKED", null, null));
        String storedBody = new ObjectMapper().writeValueAsString(stored);

        BulkLockIdempotencyPort.Record existing = new BulkLockIdempotencyPort.Record(
                42L, "idemp-replay", hash, storedBody, Instant.now());
        when(idempotencyPort.find(eq(42L), eq("idemp-replay")))
                .thenReturn(Optional.of(existing));

        BulkLockAccountResult replayed = useCase.execute(new BulkLockAccountCommand(
                List.of("acc-a"), "replay-test-reason", null, "idemp-replay", operator()));

        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.results()).hasSize(1);
        assertThat(replayed.results().get(0).outcome()).isEqualTo("LOCKED");
        verify(accountAdminUseCase, never()).lock(any());
        verify(idempotencyPort, never()).save(anyLong(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void same_key_different_payload_raises_idempotency_key_conflict() {
        stubOperatorResolution();
        BulkLockIdempotencyPort.Record existing = new BulkLockIdempotencyPort.Record(
                42L, "idemp-conflict", "deadbeef".repeat(8), "[]", Instant.now());
        when(idempotencyPort.find(anyLong(), anyString())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(new BulkLockAccountCommand(
                List.of("acc-z"), "divergent-payload", null, "idemp-conflict", operator())))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(accountAdminUseCase, never()).lock(any());
    }

    @Test
    void concurrent_first_request_race_resolves_via_replay_of_winner() throws Exception {
        stubOperatorResolution();
        List<String> ids = List.of("acc-race");

        // No prior record on the initial fast-path lookup.
        String hash = useCase.computeRequestHash(ids, "race-reason-ok", null);
        List<BulkLockAccountResult.Item> winnerItems = List.of(
                new BulkLockAccountResult.Item("acc-race", "LOCKED", null, null));
        String winnerBody = new ObjectMapper().writeValueAsString(winnerItems);
        BulkLockIdempotencyPort.Record winner = new BulkLockIdempotencyPort.Record(
                42L, "idemp-race", hash, winnerBody, Instant.now());

        when(idempotencyPort.find(eq(42L), eq("idemp-race")))
                .thenReturn(Optional.empty())     // fast-path miss
                .thenReturn(Optional.of(winner)); // race resolution read

        when(accountAdminUseCase.lock(any())).thenAnswer(inv ->
                okResult(((LockAccountCommand) inv.getArgument(0)).accountId()));

        // Losing-side save races and loses the PK race.
        doThrow(new BulkLockIdempotencyPort.DuplicateKeyException(new RuntimeException("pk collision")))
                .when(idempotencyPort).save(anyLong(), anyString(), anyString(), anyString(), any());

        BulkLockAccountResult r = useCase.execute(new BulkLockAccountCommand(
                ids, "race-reason-ok", null, "idemp-race", operator()));

        // Winner's response body is replayed.
        assertThat(r.replayed()).isTrue();
        assertThat(r.results().get(0).outcome()).isEqualTo("LOCKED");
        verify(idempotencyPort, times(2)).find(eq(42L), eq("idemp-race"));
    }

    @Test
    void concurrent_race_with_divergent_payload_raises_409() throws Exception {
        stubOperatorResolution();
        List<String> ids = List.of("acc-race-bad");

        // Winner's stored hash intentionally does not match the loser's request.
        BulkLockIdempotencyPort.Record winner = new BulkLockIdempotencyPort.Record(
                42L, "idemp-race-bad", "deadbeef".repeat(8), "[]", Instant.now());

        when(idempotencyPort.find(eq(42L), eq("idemp-race-bad")))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));

        when(accountAdminUseCase.lock(any())).thenAnswer(inv ->
                okResult(((LockAccountCommand) inv.getArgument(0)).accountId()));

        doThrow(new BulkLockIdempotencyPort.DuplicateKeyException(new RuntimeException("pk collision")))
                .when(idempotencyPort).save(anyLong(), anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> useCase.execute(new BulkLockAccountCommand(
                ids, "race-divergent-reason", null, "idemp-race-bad", operator())))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }
}
