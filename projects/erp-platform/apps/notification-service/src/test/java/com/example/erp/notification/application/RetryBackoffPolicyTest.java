package com.example.erp.notification.application;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class RetryBackoffPolicyTest {

    /** A deterministic {@link RandomGenerator} whose {@code nextDouble()} is fixed. */
    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @Test
    void midpointJitter_growsExponentially() {
        // factor = 0.8 + 0.5*0.4 = 1.0 → backoff == base
        RetryBackoffPolicy policy = new RetryBackoffPolicy(1000, 60000, fixed(0.5));
        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofMillis(1000));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofMillis(2000));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofMillis(4000));
        assertThat(policy.backoffFor(4)).isEqualTo(Duration.ofMillis(8000));
    }

    @Test
    void jitterStaysWithinPlusMinus20Percent() {
        long base = 1000;
        assertThat(new RetryBackoffPolicy(base, 60000, fixed(0.0)).backoffFor(1))
                .isEqualTo(Duration.ofMillis(800));   // lower bound 0.8
        assertThat(new RetryBackoffPolicy(base, 60000, fixed(0.999999)).backoffFor(1).toMillis())
                .isLessThanOrEqualTo(1200).isGreaterThan(1100); // approaching upper bound 1.2 (exclusive)
    }

    @Test
    void backoffIsCappedAtMax() {
        RetryBackoffPolicy policy = new RetryBackoffPolicy(1000, 60000, fixed(0.5));
        // 1000 * 2^19 would be ~500M ms; capped at 60000.
        assertThat(policy.backoffFor(20)).isEqualTo(Duration.ofMillis(60000));
        // pathological attempt number must not overflow.
        assertThat(policy.backoffFor(100)).isEqualTo(Duration.ofMillis(60000));
    }
}
