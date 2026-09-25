package com.example.auth.application;

import com.example.auth.application.command.RevokeSessionsOnAccountLockedCommand;
import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevokeSessionsOnAccountLockedUseCase 단위 테스트 — TASK-BE-601")
class RevokeSessionsOnAccountLockedUseCaseTest {

    private static final String ACCOUNT_ID = "0199aaaa-0000-7000-8000-000000000001";
    private static final String TENANT = "ecommerce";

    @Mock private ForceLogoutUseCase forceLogoutUseCase;

    /**
     * Stands in for the processed_events primary key: a second insert of the same id is
     * ignored, and a work failure "rolls back" the insert, as the real transaction does.
     */
    private final InMemoryDedupe dedupe = new InMemoryDedupe();

    private RevokeSessionsOnAccountLockedUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RevokeSessionsOnAccountLockedUseCase(dedupe, forceLogoutUseCase);
    }

    private static RevokeSessionsOnAccountLockedCommand command(UUID eventId, Instant lockedAt) {
        return new RevokeSessionsOnAccountLockedCommand(eventId, ACCOUNT_ID, TENANT, "AUTO_DETECT", lockedAt);
    }

    @Test
    @DisplayName("잠금 이벤트 → 그 계정의 세션을 폐기한다 (테넌트 한정 없는 net-zero 오버로드)")
    void lockEvent_revokesTheAccountsSessions() {
        given(forceLogoutUseCase.execute(ACCOUNT_ID))
                .willReturn(new ForceLogoutUseCase.Result(ACCOUNT_ID, 3, Instant.now()));
        Instant lockedAt = Instant.now().minusSeconds(2);

        RevokeSessionsOnAccountLockedUseCase.Result result = useCase.execute(command(UUID.randomUUID(), lockedAt));

        assertThat(result.outcome()).isEqualTo(RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED);
        assertThat(result.revokedTokenCount()).isEqualTo(3);
        assertThat(result.propagationLag()).isPositive();
        verify(forceLogoutUseCase).execute(ACCOUNT_ID);
        // The tenant-confined overload would no-op a social-only account (no credential row).
        verify(forceLogoutUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("같은 eventId 재전달 → 두 번째는 no-op, DUPLICATE 로 보고 (두 번째 폐기로 세지 않는다)")
    void duplicateEvent_isNoOpTheSecondTime() {
        given(forceLogoutUseCase.execute(ACCOUNT_ID))
                .willReturn(new ForceLogoutUseCase.Result(ACCOUNT_ID, 1, Instant.now()));
        UUID eventId = UUID.randomUUID();

        RevokeSessionsOnAccountLockedUseCase.Result first = useCase.execute(command(eventId, null));
        RevokeSessionsOnAccountLockedUseCase.Result second = useCase.execute(command(eventId, null));

        assertThat(first.outcome()).isEqualTo(RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED);
        assertThat(second.outcome()).isEqualTo(RevokeSessionsOnAccountLockedUseCase.Outcome.DUPLICATE);
        assertThat(second.revokedTokenCount()).isZero();
        verify(forceLogoutUseCase, times(1)).execute(ACCOUNT_ID);
    }

    @Test
    @DisplayName("폐기 실패 → 예외가 그대로 올라간다 (삼킴 금지), 재전달되면 다시 처리된다")
    void revokeFailure_propagates_andIsRetriedOnRedelivery() {
        UUID eventId = UUID.randomUUID();
        given(forceLogoutUseCase.execute(ACCOUNT_ID))
                .willThrow(new IllegalStateException("redis/db down"))
                .willReturn(new ForceLogoutUseCase.Result(ACCOUNT_ID, 2, Instant.now()));

        assertThatThrownBy(() -> useCase.execute(command(eventId, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis/db down");

        // The failed attempt must not leave the event marked processed — otherwise the
        // Kafka retry would be skipped as a duplicate and the session would survive.
        RevokeSessionsOnAccountLockedUseCase.Result retry = useCase.execute(command(eventId, null));
        assertThat(retry.outcome()).isEqualTo(RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED);
        assertThat(retry.revokedTokenCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("lockedAt 없음 → 지연은 null (폐기는 정상)")
    void missingLockedAt_lagIsNull() {
        given(forceLogoutUseCase.execute(ACCOUNT_ID))
                .willReturn(new ForceLogoutUseCase.Result(ACCOUNT_ID, 0, Instant.now()));

        RevokeSessionsOnAccountLockedUseCase.Result result = useCase.execute(command(UUID.randomUUID(), null));

        assertThat(result.outcome()).isEqualTo(RevokeSessionsOnAccountLockedUseCase.Outcome.REVOKED);
        assertThat(result.propagationLag()).isNull();
    }

    private static final class InMemoryDedupe implements EventDedupePort {
        private final Set<UUID> processed = new HashSet<>();

        @Override
        public Outcome process(UUID eventId, String eventType, Runnable work) {
            assertThat(eventType).isEqualTo("account.locked");
            if (!processed.add(eventId)) {
                return Outcome.IGNORED_DUPLICATE;
            }
            try {
                work.run();
            } catch (RuntimeException e) {
                processed.remove(eventId);
                throw e;
            }
            return Outcome.APPLIED;
        }
    }
}
