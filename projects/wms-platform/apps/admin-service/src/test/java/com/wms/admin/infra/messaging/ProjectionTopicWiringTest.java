package com.wms.admin.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.admin.infra.observability.TopicEventTypeMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Guard for the defect in {@code TASK-BE-582}: {@code admin-service} subscribed
 * to {@code wms.inbound.asn.v1} and {@code wms.outbound.order.v1}, which no
 * producer publishes to. Every ASN and every outbound order therefore reached
 * no projection at all, and the console's inbound / outbound screens were
 * permanently empty — with no error anywhere, because a subscription to a topic
 * nobody writes to is indistinguishable from an idle one.
 *
 * <p>The existing Kafka ITs could not catch it: each one produces to a topic
 * name it chooses itself, so the producer's name and the consumer's name never
 * met in the same execution. These two assertions close that gap without
 * needing a producer module on the classpath:
 *
 * <ol>
 *   <li>every topic in {@link TopicEventTypeMap} is one the producer's
 *       {@code TopicResolver} would actually emit for its event types;</li>
 *   <li>the {@code @KafkaListener} subscriptions — placeholders resolved
 *       against the real {@code application.yml} — are exactly that topic
 *       set.</li>
 * </ol>
 *
 * <p>Together they mean a new event type cannot be wired up without a topic
 * that the producing service really writes to.
 */
class ProjectionTopicWiringTest {

    /** {@code ${some.key:default}} */
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+):([^}]*)}$");

    private static final List<Class<?>> CONSUMERS = List.of(
            MasterProjectionConsumer.class,
            InboundProjectionConsumer.class,
            OutboundProjectionConsumer.class,
            InventoryProjectionConsumer.class);

    // ----- 1) producer naming rule -------------------------------------

    @Test
    @DisplayName("every mapped topic is the one the producing service's TopicResolver emits")
    void mappedTopicsMatchProducerNamingRule() {
        TopicEventTypeMap map = TopicEventTypeMap.defaults();

        for (String topic : map.topics()) {
            for (String eventType : map.eventTypesFor(topic)) {
                assertThat(topic)
                        .as("topic for event type '%s'", eventType)
                        .isEqualTo(producerTopicFor(eventType));
            }
        }
    }

    /**
     * The three producer-side rules, each mirrored from that service's
     * {@code TopicResolver}:
     *
     * <ul>
     *   <li>{@code master-service} — {@code MasterOutboxPublisher#topicFor}
     *       folds {@code master.<aggregate>.<action>} onto one topic per
     *       aggregate.</li>
     *   <li>{@code inventory-service} — mechanical, with one documented
     *       exception routing {@code inventory.low-stock-detected} onto the
     *       shared alert topic.</li>
     *   <li>{@code inbound-service} / {@code outbound-service} — mechanical
     *       {@code "wms." + eventType + ".v1"}.</li>
     * </ul>
     */
    private static String producerTopicFor(String eventType) {
        if (eventType.startsWith("master.")) {
            return "wms.master." + eventType.split("\\.")[1] + ".v1";
        }
        if (eventType.equals("inventory.low-stock-detected")) {
            return "wms.inventory.alert.v1";
        }
        return "wms." + eventType + ".v1";
    }

    // ----- 2) subscriptions == mapped topics ---------------------------

    @Test
    @DisplayName("@KafkaListener subscriptions resolve to exactly the mapped topics")
    void subscribedTopicsAreExactlyTheMappedTopics() throws IOException {
        Properties yml = loadApplicationYml();
        Set<String> subscribed = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (Class<?> consumer : CONSUMERS) {
            for (String expression : topicExpressionsOf(consumer)) {
                Matcher m = PLACEHOLDER.matcher(expression);
                assertThat(m.matches())
                        .as("topic expression '%s' on %s must be ${key:default}",
                                expression, consumer.getSimpleName())
                        .isTrue();
                String key = m.group(1);

                // The annotation default must never be the operative value in
                // production: application.yml has to declare the key.
                assertThat(yml)
                        .as("application.yml must declare '%s' (used by %s)",
                                key, consumer.getSimpleName())
                        .containsKey(key);

                String topic = String.valueOf(yml.get(key));
                assertThat(topic)
                        .as("annotation default for '%s' must agree with application.yml", key)
                        .isEqualTo(m.group(2));

                if (!subscribed.add(topic)) {
                    duplicates.add(topic);
                }
            }
        }

        assertThat(duplicates)
                .as("a topic subscribed twice would double-project every event")
                .isEmpty();
        assertThat(subscribed)
                .containsExactlyInAnyOrderElementsOf(TopicEventTypeMap.defaults().topics());
    }

    private static String[] topicExpressionsOf(Class<?> consumer) {
        try {
            KafkaListener listener = consumer
                    .getMethod("onMessage", ConsumerRecord.class)
                    .getAnnotation(KafkaListener.class);
            assertThat(listener).as("%s must carry @KafkaListener", consumer.getSimpleName())
                    .isNotNull();
            return listener.topics();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(consumer.getSimpleName() + " has no onMessage(ConsumerRecord)", e);
        }
    }

    /**
     * Reads the real {@code application.yml} rather than a test fixture — the
     * point of this test is that the shipped configuration is correct. The
     * value carries {@code ${ENV_VAR:default}} indirection, so unwrap one
     * level to get the default topic name.
     */
    private static Properties loadApplicationYml() throws IOException {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        yaml.afterPropertiesSet();
        Properties raw = yaml.getObject();
        assertThat(raw).as("application.yml must be readable from the test classpath").isNotNull();

        Properties resolved = new Properties();
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (!key.startsWith("admin.projection.kafka.topics.")) {
                continue;
            }
            Matcher m = PLACEHOLDER.matcher(String.valueOf(e.getValue()));
            resolved.put(key, m.matches() ? m.group(2) : String.valueOf(e.getValue()));
        }
        assertThat(resolved).as("no admin.projection.kafka.topics.* keys found").isNotEmpty();
        return resolved;
    }
}
