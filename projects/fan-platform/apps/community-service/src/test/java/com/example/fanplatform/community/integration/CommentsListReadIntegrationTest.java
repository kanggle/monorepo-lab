package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.application.PostMediaRefSerializer;
import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.infrastructure.jpa.CommentJpaRepository;
import com.example.fanplatform.community.infrastructure.jpa.PostJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code GET /api/community/posts/{postId}/comments} — TASK-FAN-BE-052, asserted through the
 * HTTP API against real persistence.
 *
 * <p>The central question this suite answers is the one the task explicitly calls out: a viewer
 * who cannot read the POST must not be able to read its COMMENTS either. The gated-post cases
 * below reuse the exact {@link MembershipGateIntegrationTest} deny-all {@link MembershipChecker}
 * pattern so the same non-member fan that gets 403 on {@code GET /posts/{id}} also gets 403
 * here, on the same post.
 */
@ContextConfiguration(classes = CommentsListReadIntegrationTest.DenyMembershipConfig.class)
class CommentsListReadIntegrationTest extends CommunityServiceIntegrationBase {

    private static final String TENANT = "fan-platform";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    CommentJpaRepository commentJpaRepository;

    @Autowired
    PostMediaRefSerializer mediaRefSerializer;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String accountId() {
        return UUID.randomUUID().toString();
    }

    private Post seedPublishedPost(String author, PostVisibility visibility) {
        Post p = Post.createDraft(
                UUID.randomUUID().toString(), TENANT, author,
                PostType.ARTIST_POST, visibility, "title", "body",
                mediaRefSerializer.serialize(null));
        p.publish(ActorType.AUTHOR);
        return postJpaRepository.saveAndFlush(p);
    }

    private Comment seedComment(String postId, String authorAccountId, String body) {
        return commentJpaRepository.save(Comment.create(
                UUID.randomUUID().toString(), TENANT, postId, authorAccountId, body));
    }

    private ResponseEntity<String> listComments(String fanId, String postId) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken(fanId));
        return rest.exchange(
                url("/api/community/posts/" + postId + "/comments"),
                HttpMethod.GET,
                new HttpEntity<>(h),
                String.class);
    }

    @Test
    @DisplayName("댓글 없는 PUBLIC 글 → 200 + 빈 content")
    void publicPostWithNoComments_returnsEmptyPage() throws Exception {
        String postId = seedPublishedPost(accountId(), PostVisibility.PUBLIC).getId();

        ResponseEntity<String> res = listComments(accountId(), postId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(res.getBody()).path("data");
        assertThat(data.path("content")).isEmpty();
        assertThat(data.path("totalElements").asLong()).isEqualTo(0);
        assertThat(data.path("hasNext").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("여러 댓글 → createdAt 오름차순(작성 순서)으로 반환되고, 삭제된 댓글은 빠진다")
    void returnsCommentsOldestFirstExcludingDeleted() throws Exception {
        Post post = seedPublishedPost(accountId(), PostVisibility.PUBLIC);
        Comment first = seedComment(post.getId(), accountId(), "first");
        Comment second = seedComment(post.getId(), accountId(), "second");
        Comment deleted = seedComment(post.getId(), accountId(), "will be deleted");
        deleted.markDeleted();
        commentJpaRepository.save(deleted);

        ResponseEntity<String> res = listComments(accountId(), post.getId());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(res.getBody()).path("data");
        assertThat(data.path("content")).hasSize(2);
        assertThat(data.path("content").get(0).path("commentId").asText()).isEqualTo(first.getId());
        assertThat(data.path("content").get(1).path("commentId").asText()).isEqualTo(second.getId());
        assertThat(data.path("totalElements").asLong())
                .as("the deleted comment must not be counted either")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("MEMBERS_ONLY 글 + 비멤버 팬 → 403 MEMBERSHIP_REQUIRED (POST /posts/{id} 와 동일 결과)")
    void membersOnlyPost_nonMemberFan_returns403() throws Exception {
        Post post = seedPublishedPost("artist-x", PostVisibility.MEMBERS_ONLY);
        seedComment(post.getId(), "artist-x", "members only comment");

        ResponseEntity<String> res = listComments("fan-non-member", post.getId());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = objectMapper.readTree(res.getBody());
        assertThat(body.path("code").asText()).isEqualTo("MEMBERSHIP_REQUIRED");
        assertThat(body.path("details").path("requiredTier").asText()).isEqualTo("MEMBERS_ONLY");
    }

    @Test
    @DisplayName("MEMBERS_ONLY 글 + 작성자 본인 → 200 (작성자는 멤버십 검사를 우회)")
    void membersOnlyPost_author_returns200() throws Exception {
        String authorId = "artist-author";
        Post post = seedPublishedPost(authorId, PostVisibility.MEMBERS_ONLY);
        seedComment(post.getId(), authorId, "members only comment");

        ResponseEntity<String> res = listComments(authorId, post.getId());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(res.getBody()).path("data");
        assertThat(data.path("content")).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 post → 404 POST_NOT_FOUND")
    void missingPost_returns404() {
        ResponseEntity<String> res = listComments(accountId(), UUID.randomUUID().toString());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("인증 없는 호출 → 401")
    void withoutBearer_returns401() {
        String postId = seedPublishedPost(accountId(), PostVisibility.PUBLIC).getId();

        ResponseEntity<String> res = rest.exchange(
                url("/api/community/posts/" + postId + "/comments"),
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Mirrors {@link MembershipGateIntegrationTest.DenyMembershipConfig} — see there for why. */
    @TestConfiguration
    static class DenyMembershipConfig {
        @Bean
        @Primary
        MembershipChecker denyAllMembershipChecker() {
            return new DenyAllMembershipChecker();
        }
    }

    static class DenyAllMembershipChecker implements MembershipChecker {
        @Override
        public boolean hasAccess(String accountId, String tier, String tenantId) {
            return false;
        }
    }
}
