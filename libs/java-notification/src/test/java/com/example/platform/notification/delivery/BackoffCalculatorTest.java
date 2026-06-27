package com.example.platform.notification.delivery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffCalculatorTest {

    private static final List<Integer> SCHEDULE = List.of(1, 5, 30, 120, 600);

    @ParameterizedTest(name = "attempt {0} -> base {1}s (clamped at last index)")
    @CsvSource({
            "0, 1",
            "1, 5",
            "2, 30",
            "3, 120",
            "4, 600",
            "5, 600",   // beyond schedule length -> clamps to last entry
            "9, 600"
    })
    void baseSeconds_indexesScheduleAndClampsAtLast(int attempt, long expectedBase) {
        BackoffCalculator calc = new BackoffCalculator(
                SCHEDULE, 0.2, 5, BackoffCalculator.NO_JITTER);

        assertThat(calc.baseSeconds(attempt)).isEqualTo(expectedBase);
    }

    @Test
    void noJitter_yieldsExactBaseDelay() {
        BackoffCalculator calc = new BackoffCalculator(
                SCHEDULE, 0.2, 5, BackoffCalculator.NO_JITTER);

        assertThat(calc.backoff(2)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void jitter_staysWithinPlusMinusRatioBounds() {
        // base for attempt 2 = 30s, ratio 0.2 -> offset in [-6s, +6s] -> [24s, 36s].
        double ratio = 0.2;
        long base = 30;
        BackoffCalculator low = new BackoffCalculator(SCHEDULE, ratio, 5, () -> -1.0);
        BackoffCalculator high = new BackoffCalculator(SCHEDULE, ratio, 5, () -> 1.0);

        long expectedLowMs = (long) ((base - base * ratio) * 1000); // 24_000
        long expectedHighMs = (long) ((base + base * ratio) * 1000); // 36_000

        assertThat(low.backoff(2).toMillis()).isEqualTo(expectedLowMs);
        assertThat(high.backoff(2).toMillis()).isEqualTo(expectedHighMs);
    }

    @Test
    void jitter_negativeBeyondBase_isFlooredAtZero() {
        // A pathological jitter source returning a fraction that would push below 0.
        // With ratio 1.0 and fraction -1.0 the offset == -base, so the result is 0.
        BackoffCalculator calc = new BackoffCalculator(SCHEDULE, 1.0, 5, () -> -1.0);

        assertThat(calc.backoff(0)).isEqualTo(Duration.ZERO);
    }

    @Test
    void nextRetryAt_addsBackoffToNow() {
        Instant now = Instant.parse("2026-06-28T00:00:00Z");
        BackoffCalculator calc = new BackoffCalculator(SCHEDULE, 0.2, 5, BackoffCalculator.NO_JITTER);

        assertThat(calc.nextRetryAt(1, now)).isEqualTo(now.plusSeconds(5));
    }

    @Test
    void maxAttempts_isExposed() {
        assertThat(new BackoffCalculator(SCHEDULE, 0.2, 7, BackoffCalculator.NO_JITTER).maxAttempts())
                .isEqualTo(7);
    }

    @Test
    void defaults_matchTheWmsReferenceSchedule() {
        BackoffCalculator calc = new BackoffCalculator();

        assertThat(BackoffCalculator.DEFAULT_BACKOFF_SECONDS).containsExactly(1, 5, 30, 120, 600);
        assertThat(BackoffCalculator.DEFAULT_JITTER_RATIO).isEqualTo(0.2);
        assertThat(calc.maxAttempts()).isEqualTo(5);
    }

    @Test
    void rejectsEmptySchedule() {
        assertThatThrownBy(() -> new BackoffCalculator(List.of(), 0.2, 5, BackoffCalculator.NO_JITTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxAttempts() {
        assertThatThrownBy(() -> new BackoffCalculator(SCHEDULE, 0.2, 0, BackoffCalculator.NO_JITTER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
