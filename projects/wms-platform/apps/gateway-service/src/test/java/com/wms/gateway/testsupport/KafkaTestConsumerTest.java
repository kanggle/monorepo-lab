package com.wms.gateway.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * No-Docker compile / lifecycle smoke test for {@link KafkaTestConsumer}.
 * Cannot exercise the poll loop against a real broker here — that path is
 * verified implicitly by the e2e suite. What we DO verify:
 *
 * <ul>
 *   <li>The class compiles with the declared test dependencies.</li>
 *   <li>Constructing against a deliberately unreachable bootstrap address
 *       either throws promptly (network-level) or yields a consumer we can
 *       close cleanly without hanging. This guards against classpath /
 *       serializer-config regressions that would manifest in the live-pair
 *       suite as opaque timeouts.</li>
 * </ul>
 */
class KafkaTestConsumerTest {

    @Test
    void constructorEitherThrowsOrProducesAQuicklyClosableConsumer() {
        // Use an obviously dead host:port. Kafka client's bootstrap resolution
        // is lazy — it will spawn a background thread trying to connect, which
        // we stop via close(). The point of the self-test is to prove the
        // class wires its deps together, not to hit a real broker.
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(
                "127.0.0.1:1",  // port 1 is reserved and will never accept TCP
                List.of("master.warehouse.v1"))) {
            assertThat(consumer.drain()).isEmpty();
        }
        // If close() did not complete within its internal 5s budget the JVM
        // would still be waiting here — reaching this line proves the
        // lifecycle is sound.
    }
}
