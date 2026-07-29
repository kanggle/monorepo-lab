package com.example.fanplatform.notification.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-FAN-BE-026 — envelope parsing for the two consumed community topics,
 * including the rollout tolerance the contract mandates (absent
 * {@code postAuthorAccountId} / {@code mentionedAccountIds} are NOT parse errors).
 */
class CommunityEventParserTest {

    private final CommunityEventParser parser = new CommunityEventParser(new ObjectMapper());

    private static String commentEnvelope() {
        return """
                {"eventId":"evt-c1","eventType":"community.comment.added","source":"fan-platform-community-service",
                 "occurredAt":"2026-07-22T08:59:00Z","schemaVersion":1,"partitionKey":"post-1",
                 "payload":{"postId":"post-1","tenantId":"fan-platform","commentId":"cmt-1",
                   "authorAccountId":"fan-1","postAuthorAccountId":"author-1",
                   "mentionedAccountIds":["fan-2","fan-3"],"occurredAt":"2026-07-22T08:59:00Z"}}
                """;
    }

    private static String reactionEnvelope() {
        return """
                {"eventId":"evt-r1","eventType":"community.reaction.added","source":"fan-platform-community-service",
                 "occurredAt":"2026-07-22T09:01:00Z","schemaVersion":1,"partitionKey":"post-1",
                 "payload":{"postId":"post-1","tenantId":"fan-platform","reactorAccountId":"fan-1",
                   "postAuthorAccountId":"author-1","reactionType":"LIKE",
                   "occurredAt":"2026-07-22T09:01:00Z"}}
                """;
    }

    @Test
    @DisplayName("parses a comment.added envelope incl. the recipient-routing fields")
    void parsesCommentAdded() {
        CommunityEvent e = parser.parse(commentEnvelope());
        assertThat(e.eventId()).isEqualTo("evt-c1");
        assertThat(e.eventType()).isEqualTo("community.comment.added");
        assertThat(e.tenantId()).isEqualTo("fan-platform");
        assertThat(e.postId()).isEqualTo("post-1");
        assertThat(e.commentId()).isEqualTo("cmt-1");
        assertThat(e.actorAccountId()).isEqualTo("fan-1");
        assertThat(e.postAuthorAccountId()).isEqualTo("author-1");
        assertThat(e.mentionedAccountIds()).containsExactly("fan-2", "fan-3");
        assertThat(e.reactionType()).isNull();
        assertThat(e.occurredAt()).isEqualTo(Instant.parse("2026-07-22T08:59:00Z"));
    }

    @Test
    @DisplayName("parses a reaction.added envelope incl. postAuthorAccountId + reactionType")
    void parsesReactionAdded() {
        CommunityEvent e = parser.parse(reactionEnvelope());
        assertThat(e.eventType()).isEqualTo("community.reaction.added");
        assertThat(e.actorAccountId()).isEqualTo("fan-1");
        assertThat(e.postAuthorAccountId()).isEqualTo("author-1");
        assertThat(e.reactionType()).isEqualTo("LIKE");
        assertThat(e.commentId()).isNull();
        assertThat(e.mentionedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("absent mentionedAccountIds key → empty list (rollout tolerance, not a failure)")
    void missingMentionListParsesAsEmpty() {
        String env = commentEnvelope()
                .replace("\"mentionedAccountIds\":[\"fan-2\",\"fan-3\"],", "");
        CommunityEvent e = parser.parse(env);
        assertThat(e.mentionedAccountIds()).isEmpty();
        assertThat(e.postAuthorAccountId()).isEqualTo("author-1");
    }

    @Test
    @DisplayName("explicitly empty mentionedAccountIds → empty list (the producer's normal case today)")
    void emptyMentionListParsesAsEmpty() {
        String env = commentEnvelope()
                .replace("[\"fan-2\",\"fan-3\"]", "[]");
        assertThat(parser.parse(env).mentionedAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("absent postAuthorAccountId (pre-enrichment event) → null, NOT a MalformedEventException")
    void missingPostAuthorParsesAsNull() {
        String env = commentEnvelope().replace("\"postAuthorAccountId\":\"author-1\",", "");
        CommunityEvent e = parser.parse(env);
        assertThat(e.postAuthorAccountId()).isNull();
        assertThat(e.actorAccountId()).isEqualTo("fan-1");
    }

    @Test
    @DisplayName("absent postAuthorAccountId on a reaction event → null, NOT a failure")
    void missingPostAuthorOnReactionParsesAsNull() {
        String env = reactionEnvelope().replace("\"postAuthorAccountId\":\"author-1\",", "");
        assertThat(parser.parse(env).postAuthorAccountId()).isNull();
    }

    @Test
    @DisplayName("tolerates unknown payload fields (forward compatibility)")
    void toleratesUnknownFields() {
        String env = commentEnvelope().replace("\"commentId\":\"cmt-1\"",
                "\"commentId\":\"cmt-1\",\"futureField\":\"ignored\"");
        assertThat(parser.parse(env).commentId()).isEqualTo("cmt-1");
    }

    @Test
    @DisplayName("unsupported schemaVersion → UnsupportedSchemaVersionException")
    void unsupportedSchema() {
        String env = commentEnvelope().replace("\"schemaVersion\":1", "\"schemaVersion\":99");
        assertThatThrownBy(() -> parser.parse(env))
                .isInstanceOf(UnsupportedSchemaVersionException.class);
    }

    @Test
    @DisplayName("unparseable JSON → MalformedEventException")
    void malformedJson() {
        assertThatThrownBy(() -> parser.parse("this is not json {{{"))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    @DisplayName("missing required payload field (authorAccountId) → MalformedEventException")
    void missingRequiredField() {
        String env = commentEnvelope().replace("\"authorAccountId\":\"fan-1\",", "");
        assertThatThrownBy(() -> parser.parse(env))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    @DisplayName("missing required payload field (postId) → MalformedEventException")
    void missingPostId() {
        String env = reactionEnvelope().replace("\"postId\":\"post-1\",", "");
        assertThatThrownBy(() -> parser.parse(env))
                .isInstanceOf(MalformedEventException.class);
    }

    @Test
    @DisplayName("an unrelated community eventType (post.published) → MalformedEventException (not consumed here)")
    void unsupportedEventType() {
        String env = commentEnvelope().replace("community.comment.added", "community.post.published");
        assertThatThrownBy(() -> parser.parse(env))
                .isInstanceOf(MalformedEventException.class);
    }
}
