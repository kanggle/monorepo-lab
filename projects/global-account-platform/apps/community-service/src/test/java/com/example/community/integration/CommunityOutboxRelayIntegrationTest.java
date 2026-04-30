package com.example.community.integration;

import org.junit.jupiter.api.Tag;
import com.example.community.application.ActorContext;
import com.example.community.application.AddCommentUseCase;
import com.example.community.application.AddReactionUseCase;
import com.example.community.application.PublishPostCommand;
import com.example.community.application.PublishPostUseCase;
import com.example.community.application.PostView;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Outbox relay integration test (TASK-BE-149).
 *
 * <p>Verifies that {@code community.post.published}, {@code community.comment.created},
 * and {@code community.reaction.added} are relayed from the outbox to Kafka with the
 * standard envelope format.
 */
@Tag("integration")
@SpringBootTest
@DisplayName("CommunityOutboxRelay 통합 테스트")
class CommunityOutboxRelayIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private PublishPostUseCase publishPostUseCase;

    @Autowired
    private AddCommentUseCase addCommentUseCase;

    @Autowired
    private AddReactionUseCase addReactionUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private void stubAccountProfile() {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"x\",\"displayName\":\"Test\"}")));
    }

    private void stubMembershipAllowed(String accountId) {
        MEMBERSHIP_WM.stubFor(get(urlPathEqualTo("/internal/membership/access"))
                .willReturn(okJson("{\"accountId\":\"" + accountId
                        + "\",\"requiredPlanLevel\":\"FAN_CLUB\""
                        + ",\"allowed\":true,\"activePlanLevel\":\"FAN_CLUB\"}")));
    }

    private KafkaConsumer<String, String> createConsumer(String... topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-relay-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topics));
        return consumer;
    }

    private static List<ConsumerRecord<String, String>> drain(KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            out.add(record);
        }
        return out;
    }

    @Test
    @DisplayName("publishPost 호출 시 community.post.published 가 envelope 와 함께 Kafka 에 릴레이된다")
    void publishPost_relaysToKafka_withCorrectEnvelope() {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        ActorContext actor = new ActorContext(artistId, Set.of("ARTIST"));

        try (KafkaConsumer<String, String> consumer = createConsumer("community.post.published")) {
            PostView view = transactionTemplate.execute(s ->
                    publishPostUseCase.execute(new PublishPostCommand(
                            actor,
                            PostType.ARTIST_POST,
                            PostVisibility.PUBLIC,
                            "Relay Title",
                            "Relay body",
                            List.of()
                    )));
            assertThat(view).isNotNull();

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<ConsumerRecord<String, String>> records = drain(consumer);
                List<ConsumerRecord<String, String>> matching = records.stream()
                        .filter(r -> r.value().contains(view.postId()))
                        .toList();
                assertThat(matching).isNotEmpty();

                JsonNode envelope = objectMapper.readTree(matching.get(0).value());
                assertThat(envelope.get("eventType").asText()).isEqualTo("community.post.published");
                assertThat(envelope.get("source").asText()).isEqualTo("community-service");
                assertThat(envelope.get("partitionKey").asText()).isEqualTo(view.postId());
                assertThat(envelope.has("payload")).isTrue();

                JsonNode payload = envelope.get("payload");
                assertThat(payload.get("postId").asText()).isNotBlank();
                assertThat(payload.get("authorAccountId").asText()).isEqualTo(artistId);
            });
        }
    }

    @Test
    @DisplayName("addComment 호출 시 community.comment.created 가 Kafka 에 릴레이된다")
    void addComment_relaysToKafka() {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        ActorContext artist = new ActorContext(artistId, Set.of("ARTIST"));
        ActorContext fan = new ActorContext(fanId, Set.of("FAN"));

        // Publish a post first.
        PostView publishedPost = transactionTemplate.execute(s ->
                publishPostUseCase.execute(new PublishPostCommand(
                        artist,
                        PostType.ARTIST_POST,
                        PostVisibility.PUBLIC,
                        "Comment Target",
                        "body",
                        List.of()
                )));
        assertThat(publishedPost).isNotNull();
        String postId = publishedPost.postId();

        try (KafkaConsumer<String, String> consumer = createConsumer("community.comment.created")) {
            transactionTemplate.executeWithoutResult(s ->
                    addCommentUseCase.execute(postId, "Nice post!", fan));

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<ConsumerRecord<String, String>> records = drain(consumer);
                List<ConsumerRecord<String, String>> matching = records.stream()
                        .filter(r -> postId.equals(r.key()))
                        .toList();
                assertThat(matching).isNotEmpty();

                JsonNode envelope = objectMapper.readTree(matching.get(0).value());
                assertThat(envelope.get("eventType").asText()).isEqualTo("community.comment.created");
                assertThat(envelope.get("source").asText()).isEqualTo("community-service");
                assertThat(envelope.get("partitionKey").asText()).isEqualTo(postId);

                JsonNode payload = envelope.get("payload");
                assertThat(payload.get("postId").asText()).isEqualTo(postId);
                assertThat(payload.get("commenterAccountId").asText()).isEqualTo(fanId);
            });
        }
    }

    @Test
    @DisplayName("addReaction 호출 시 community.reaction.added 가 Kafka 에 릴레이된다")
    void addReaction_relaysToKafka() {
        stubAccountProfile();
        String artistId = "artist-" + UUID.randomUUID();
        String fanId = "fan-" + UUID.randomUUID();
        ActorContext artist = new ActorContext(artistId, Set.of("ARTIST"));
        ActorContext fan = new ActorContext(fanId, Set.of("FAN"));
        stubMembershipAllowed(fanId);

        PostView publishedPost = transactionTemplate.execute(s ->
                publishPostUseCase.execute(new PublishPostCommand(
                        artist,
                        PostType.ARTIST_POST,
                        PostVisibility.PUBLIC,
                        "Reaction Target",
                        "body",
                        List.of()
                )));
        assertThat(publishedPost).isNotNull();
        String postId = publishedPost.postId();

        try (KafkaConsumer<String, String> consumer = createConsumer("community.reaction.added")) {
            transactionTemplate.executeWithoutResult(s ->
                    addReactionUseCase.execute(postId, "HEART", fan));

            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<ConsumerRecord<String, String>> records = drain(consumer);
                List<ConsumerRecord<String, String>> matching = records.stream()
                        .filter(r -> postId.equals(r.key()))
                        .toList();
                assertThat(matching).isNotEmpty();

                JsonNode envelope = objectMapper.readTree(matching.get(0).value());
                assertThat(envelope.get("eventType").asText()).isEqualTo("community.reaction.added");
                assertThat(envelope.get("source").asText()).isEqualTo("community-service");
                assertThat(envelope.get("partitionKey").asText()).isEqualTo(postId);

                JsonNode payload = envelope.get("payload");
                assertThat(payload.get("postId").asText()).isEqualTo(postId);
                assertThat(payload.get("reactorAccountId").asText()).isEqualTo(fanId);
                assertThat(payload.get("emojiCode").asText()).isEqualTo("HEART");
            });
        }
    }
}
