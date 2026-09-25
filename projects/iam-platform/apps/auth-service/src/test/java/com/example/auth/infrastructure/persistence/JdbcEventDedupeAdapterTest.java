package com.example.auth.infrastructure.persistence;

import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcEventDedupeAdapter 단위 테스트 — TASK-BE-601")
class JdbcEventDedupeAdapterTest {

    private static final UUID EVENT_ID = UUID.fromString("0199bbbb-0000-7000-8000-000000000001");

    @Mock private JdbcOperations jdbcOperations;

    private void inserted(int rows) {
        given(jdbcOperations.update(eq(JdbcEventDedupeAdapter.INSERT_SQL),
                eq(EVENT_ID.toString()), eq("account.locked"), any(Timestamp.class)))
                .willReturn(rows);
    }

    @Test
    @DisplayName("첫 삽입(1행) → work 실행, APPLIED")
    void firstSighting_runsWork() {
        inserted(1);
        AtomicInteger runs = new AtomicInteger();

        EventDedupePort.Outcome outcome = new JdbcEventDedupeAdapter(jdbcOperations)
                .process(EVENT_ID, "account.locked", runs::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("PK 충돌(0행) → work 미실행, IGNORED_DUPLICATE")
    void duplicate_skipsWork() {
        inserted(0);
        AtomicInteger runs = new AtomicInteger();

        EventDedupePort.Outcome outcome = new JdbcEventDedupeAdapter(jdbcOperations)
                .process(EVENT_ID, "account.locked", runs::incrementAndGet);

        assertThat(outcome).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("work 예외 → 그대로 전파 (트랜잭션 롤백이 dedupe 행도 지운다)")
    void workFailure_propagates() {
        inserted(1);

        assertThatThrownBy(() -> new JdbcEventDedupeAdapter(jdbcOperations)
                .process(EVENT_ID, "account.locked", () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("eventType 이 컬럼(100자)을 넘으면 삽입 전에 거부 — INSERT IGNORE 가 절단을 «중복» 으로 읽지 않게")
    void overlongEventType_isRejectedBeforeInsert() {
        assertThatThrownBy(() -> new JdbcEventDedupeAdapter(jdbcOperations)
                .process(EVENT_ID, "x".repeat(101), () -> { }))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbcOperations);
    }
}
