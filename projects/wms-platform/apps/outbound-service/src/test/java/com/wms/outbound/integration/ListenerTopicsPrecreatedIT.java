package com.wms.outbound.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * Pins {@link OutboundServiceIntegrationBase#LISTENER_TOPICS} to the topics the service's
 * {@code @KafkaListener}s actually subscribe to (TASK-BE-504).
 *
 * <p>The base class pre-creates that list before the Spring context starts, so the consumer
 * group's subscription metadata is final before the first consumer joins and the startup
 * rebalance cascade collapses into one rebalance. That only holds while the list is
 * <i>complete</i>: a listener whose topic is missing from it goes back to being lazily
 * auto-created, and the churn it causes returns for the whole group — a hand-kept list with
 * nothing checking it is exactly how a guard rots.
 *
 * <p>Both directions are asserted. A missing entry lets the churn back in; a stale entry means
 * we create a topic nothing consumes, which is dead setup that quietly outlives the listener
 * it was written for.
 */
class ListenerTopicsPrecreatedIT extends OutboundServiceIntegrationBase {

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Test
    @DisplayName("LISTENER_TOPICS is exactly the set of topics the @KafkaListeners subscribe to")
    void precreatedTopicsMatchTheListeners() {
        Set<String> subscribed = listenerRegistry.getListenerContainers().stream()
                .map(MessageListenerContainer::getContainerProperties)
                .map(props -> props.getTopics() == null ? new String[0] : props.getTopics())
                .flatMap(Arrays::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(subscribed)
                .as("no @KafkaListener resolved a topic — the registry lookup is broken, not the list")
                .isNotEmpty();

        assertThat(subscribed)
                .as("a @KafkaListener subscribes to a topic that OutboundServiceIntegrationBase does not "
                        + "pre-create. It will be lazily auto-created instead, and the group will rebalance "
                        + "when it appears — the churn TASK-BE-504 removed. Add it to LISTENER_TOPICS.")
                .isSubsetOf(LISTENER_TOPICS);

        assertThat(LISTENER_TOPICS)
                .as("LISTENER_TOPICS names a topic no @KafkaListener consumes — dead setup that outlived "
                        + "its listener. Remove it.")
                .isSubsetOf(subscribed);
    }
}
