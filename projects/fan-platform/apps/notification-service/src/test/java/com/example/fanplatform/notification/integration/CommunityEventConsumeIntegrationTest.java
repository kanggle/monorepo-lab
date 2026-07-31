package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.infrastructure.jpa.NotificationJpaRepository;
import com.example.fanplatform.notification.testsupport.EventIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TASK-FAN-BE-026 — publish {@code community.comment.added.v1} → a REPLY
 * notification row; publish {@code community.reaction.added.v1} → a
 * REACTION_BADGE row; a self-interaction produces no row (AC-3).
 *
 * <p>The V3 migration is the thing under test as much as the consumer: the new
 * {@code type} values must pass {@code ck_notification_type}, {@code membership_id}
 * must accept NULL, and {@code post_id} must exist. A Docker-free {@code :check}
 * slice cannot catch a CHECK/NOT-NULL violation (§16) — this Testcontainers IT is
 * the authoritative gate.
 */
class CommunityEventConsumeIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private NotificationJpaRepository notifications;

    @BeforeEach
    void setUp() {
        truncateAll();
        awaitListenersAssigned();
    }

    @Test
    @DisplayName("comment.added.v1 → REPLY notification persisted for the post author (post-correlated, null membership)")
    void commentCreatesReply() {
        producer().send(TOPIC_COMMENT_ADDED, "post-1",
                commentAddedEnvelope(EventIds.uuid("evt-c-it-1"), "post-1", "fan-1", "author-1", "[]"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var all = notifications.findAll();
            assertThat(all).hasSize(1);
            var row = all.get(0);
            assertThat(row.getType()).isEqualTo(NotificationType.REPLY);
            assertThat(row.getStatus()).isEqualTo(NotificationStatus.UNREAD);
            assertThat(row.getAccountId()).isEqualTo("author-1");
            assertThat(row.getPostId()).isEqualTo("post-1");
            assertThat(row.getMembershipId()).isNull();
            assertThat(row.getSourceEventId()).isEqualTo(EventIds.uuid("evt-c-it-1"));
            assertThat(row.getTitle()).isEqualTo("New reply on your post");
        });
    }

    @Test
    @DisplayName("comment.added.v1 with mentions → REPLY + one MENTION row per mentioned account (composite unique key)")
    void commentWithMentionsFansOut() {
        producer().send(TOPIC_COMMENT_ADDED, "post-2",
                commentAddedEnvelope(EventIds.uuid("evt-c-it-2"), "post-2", "fan-1", "author-2",
                        "[\"fan-2\",\"fan-3\"]"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var all = notifications.findAll();
            assertThat(all).hasSize(3);
            assertThat(all).extracting(n -> n.getType().name())
                    .containsExactlyInAnyOrder("REPLY", "MENTION", "MENTION");
            assertThat(all).extracting(n -> n.getAccountId())
                    .containsExactlyInAnyOrder("author-2", "fan-2", "fan-3");
            assertThat(all).allSatisfy(n ->
                    assertThat(n.getSourceEventId()).isEqualTo(EventIds.uuid("evt-c-it-2")));
        });
    }

    @Test
    @DisplayName("reaction.added.v1 → REACTION_BADGE notification persisted for the post author")
    void reactionCreatesBadge() {
        producer().send(TOPIC_REACTION_ADDED, "post-3",
                reactionAddedEnvelope(EventIds.uuid("evt-r-it-1"), "post-3", "fan-1", "author-3", "LOVE"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var all = notifications.findAll();
            assertThat(all).hasSize(1);
            var row = all.get(0);
            assertThat(row.getType()).isEqualTo(NotificationType.REACTION_BADGE);
            assertThat(row.getAccountId()).isEqualTo("author-3");
            assertThat(row.getPostId()).isEqualTo("post-3");
            assertThat(row.getMembershipId()).isNull();
            assertThat(row.getTitle()).isEqualTo("Someone reacted to your post");
            assertThat(row.getBody()).contains("LOVE");
        });
    }

    @Test
    @DisplayName("AC-3: self-interaction (actor == post author) creates NO row, and does not stall the partition")
    void selfInteractionCreatesNoRow() {
        // Both self-events are suppressed; the trailing third-party reaction proves
        // the partition kept moving (a stalled/DLQ'd suppression would never arrive).
        producer().send(TOPIC_COMMENT_ADDED, "post-4",
                commentAddedEnvelope(EventIds.uuid("evt-c-it-3"), "post-4", "author-4", "author-4", "[]"));
        producer().send(TOPIC_REACTION_ADDED, "post-4",
                reactionAddedEnvelope(EventIds.uuid("evt-r-it-2"), "post-4", "author-4", "author-4", "LIKE"));
        producer().send(TOPIC_REACTION_ADDED, "post-4",
                reactionAddedEnvelope(EventIds.uuid("evt-r-it-3"), "post-4", "fan-9", "author-4", "FIRE"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var all = notifications.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).getType()).isEqualTo(NotificationType.REACTION_BADGE);
            assertThat(all.get(0).getSourceEventId()).isEqualTo(EventIds.uuid("evt-r-it-3"));
        });
    }

    @Test
    @DisplayName("AC-4: duplicate community eventId → exactly one notification row")
    void duplicateDeliveryProducesSingleRow() {
        String envelope = reactionAddedEnvelope(EventIds.uuid("evt-r-it-dup"), "post-5", "fan-1", "author-5", "LIKE");
        producer().send(TOPIC_REACTION_ADDED, "post-5", envelope);
        producer().send(TOPIC_REACTION_ADDED, "post-5", envelope);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(notifications.count()).isEqualTo(1));
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(notifications.count()).isEqualTo(1));
    }
}
