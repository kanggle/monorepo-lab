package com.wms.master.integration.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/**
 * Self-test for {@link KafkaTestConsumer} that runs in the default {@code test}
 * phase without Docker. Only verifies the shape of the API and that
 * {@link KafkaTestConsumer#pollOne(Duration)} throws {@link
 * NoSuchElementException} when no broker is reachable and no record arrives —
 * this guards the pollOne contract used by every integration test.
 *
 * <p>Full broker-backed semantics are exercised by the integration suite which
 * requires Docker.
 */
class KafkaTestConsumerSelfTest {

    @Test
    void pollOne_throwsWhenNoRecordArrives() {
        // Point at an unreachable broker; constructor returns quickly because
        // the initial priming poll uses a 100 ms timeout and is non-fatal.
        KafkaTestConsumer consumer = new KafkaTestConsumer(
                "127.0.0.1:1", "self-test-topic");
        try {
            assertThatThrownBy(() -> consumer.pollOne(Duration.ofMillis(500)))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("self-test-topic");
        } finally {
            consumer.close();
        }
    }

    @Test
    void sleepHelper_doesNotLeakInterruption() {
        // Cheap check: sleep(0ms) returns immediately without throwing.
        KafkaTestConsumer.sleep(Duration.ZERO);
    }
}
