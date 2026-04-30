package com.example.community.application;

import com.example.community.application.exception.MembershipRequiredException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PostAccessGuardTest {

    private static final String AUTHOR_ID = "artist-1";
    private static final String OTHER_ID = "fan-1";

    @Mock PostRepository postRepository;
    @Mock ContentAccessChecker contentAccessChecker;

    PostAccessGuard guard;

    @Test
    @DisplayName("PUBLISHED + PUBLIC 게시글이면 접근을 허용하고 Post를 반환한다")
    void requirePublishedAccess_publishedPublicPost_returnsPost() {
        guard = new PostAccessGuard(postRepository, contentAccessChecker);
        Post post = publishedPost(PostVisibility.PUBLIC);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        Post returned = guard.requirePublishedAccess("post-1", actor(OTHER_ID));

        assertThat(returned).isSameAs(post);
    }

    @Test
    @DisplayName("게시글이 존재하지 않으면 PostNotFoundException을 던진다")
    void requirePublishedAccess_postMissing_throwsPostNotFound() {
        guard = new PostAccessGuard(postRepository, contentAccessChecker);
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requirePublishedAccess("missing", actor(OTHER_ID)))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("게시글이 PUBLISHED 상태가 아니면 PostNotFoundException을 던진다")
    void requirePublishedAccess_notPublishedPost_throwsPostNotFound() {
        guard = new PostAccessGuard(postRepository, contentAccessChecker);
        Post draft = draftPost(PostVisibility.PUBLIC);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> guard.requirePublishedAccess("post-1", actor(OTHER_ID)))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("MEMBERS_ONLY 게시글에 비작성자가 구독 없이 접근하면 MembershipRequiredException을 던진다")
    void requirePublishedAccess_membersOnlyNonAuthorWithoutSubscription_throwsMembershipRequired() {
        guard = new PostAccessGuard(postRepository, contentAccessChecker);
        Post post = publishedPost(PostVisibility.MEMBERS_ONLY);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(contentAccessChecker.check(OTHER_ID, GetPostUseCase.REQUIRED_PLAN_LEVEL))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requirePublishedAccess("post-1", actor(OTHER_ID)))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("MEMBERS_ONLY 게시글이라도 작성자 본인이면 멤버십 검사 없이 접근을 허용한다")
    void requirePublishedAccess_membersOnlyAuthor_returnsPost() {
        guard = new PostAccessGuard(postRepository, contentAccessChecker);
        Post post = publishedPost(PostVisibility.MEMBERS_ONLY);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        Post returned = guard.requirePublishedAccess("post-1", actor(AUTHOR_ID));

        assertThat(returned).isSameAs(post);
    }

    @Test
    @DisplayName("MEMBERS_ONLY 게시글에 비작성자가 구독을 보유한 경우 접근을 허용한다")
    void requirePublishedAccess_membersOnlyNonAuthorWithSubscription_returnsPost() {
        guard = new PostAccessGuard(postRepository, contentAccessChecker);
        Post post = publishedPost(PostVisibility.MEMBERS_ONLY);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(contentAccessChecker.check(OTHER_ID, GetPostUseCase.REQUIRED_PLAN_LEVEL))
                .thenReturn(true);

        Post returned = guard.requirePublishedAccess("post-1", actor(OTHER_ID));

        assertThat(returned).isSameAs(post);
    }

    private static ActorContext actor(String accountId) {
        return new ActorContext(accountId, Set.of("FAN"));
    }

    private static Post draftPost(PostVisibility visibility) {
        return Post.createDraft(AUTHOR_ID, PostType.ARTIST_POST, visibility, "title", "body", null);
    }

    private static Post publishedPost(PostVisibility visibility) {
        Post post = draftPost(visibility);
        post.publish(ActorType.AUTHOR);
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
        return post;
    }
}
