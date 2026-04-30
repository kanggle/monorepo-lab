package com.example.community.application;

import com.example.community.application.exception.MembershipRequiredException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.domain.access.ContentAccessChecker;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.domain.reaction.Reaction;
import com.example.community.domain.reaction.ReactionRepository;
import org.junit.jupiter.api.BeforeEach;
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
class GetPostUseCaseTest {

    @Mock PostRepository postRepository;
    @Mock CommentRepository commentRepository;
    @Mock ReactionRepository reactionRepository;
    @Mock ContentAccessChecker contentAccessChecker;
    @Mock AccountProfileLookup accountProfileLookup;

    GetPostUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPostUseCase(postRepository, commentRepository, reactionRepository, contentAccessChecker, accountProfileLookup);
    }

    @Test
    @DisplayName("PUBLISHED 공개 포스트 조회 시 PostView 가 반환된다")
    void execute_publishedPublicPost_returnsPostView() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "Title", "Body", null);
        post.publish(ActorType.AUTHOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("post-1")).thenReturn(3L);
        when(reactionRepository.countByPostId("post-1")).thenReturn(7L);
        when(reactionRepository.find("post-1", "fan-1")).thenReturn(Optional.empty());
        when(accountProfileLookup.displayNameOf("artist-1")).thenReturn("Artist");

        PostView result = useCase.execute("post-1", actor);

        assertThat(result.authorAccountId()).isEqualTo("artist-1");
        assertThat(result.title()).isEqualTo("Title");
        assertThat(result.commentCount()).isEqualTo(3L);
        assertThat(result.reactionCount()).isEqualTo(7L);
        assertThat(result.myReaction()).isNull();
        assertThat(result.status()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("존재하지 않는 포스트 조회 시 PostNotFoundException 이 발생한다")
    void execute_postNotFound_throwsPostNotFoundException() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing", actor))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("DELETED 포스트 조회 시 PostNotFoundException 이 발생한다")
    void execute_deletedPost_throwsPostNotFoundException() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        post.changeStatus(PostStatus.DELETED, ActorType.AUTHOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> useCase.execute("post-1", actor))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("DRAFT 포스트 조회 시 PostNotFoundException 이 발생한다")
    void execute_draftPost_throwsPostNotFoundException() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> useCase.execute("post-1", actor))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("HIDDEN 포스트를 비작성자가 조회하면 PostNotFoundException 이 발생한다")
    void execute_hiddenPost_nonAuthor_throwsPostNotFoundException() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        post.changeStatus(PostStatus.HIDDEN, ActorType.OPERATOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> useCase.execute("post-1", actor))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("HIDDEN 포스트를 작성자 본인이 조회하면 성공한다")
    void execute_hiddenPost_author_returnsPostView() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        post.changeStatus(PostStatus.HIDDEN, ActorType.OPERATOR);
        ActorContext actor = new ActorContext("artist-1", Set.of("ARTIST"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("post-1")).thenReturn(0L);
        when(reactionRepository.countByPostId("post-1")).thenReturn(0L);
        when(reactionRepository.find("post-1", "artist-1")).thenReturn(Optional.empty());
        when(accountProfileLookup.displayNameOf("artist-1")).thenReturn("Artist");

        PostView result = useCase.execute("post-1", actor);

        assertThat(result.status()).isEqualTo(PostStatus.HIDDEN);
    }

    @Test
    @DisplayName("멤버십 전용 포스트에 멤버십 없는 팬 접근 시 MembershipRequiredException 이 발생한다")
    void execute_membersOnlyPost_nonMember_throwsMembershipRequiredException() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(contentAccessChecker.check("fan-1", GetPostUseCase.REQUIRED_PLAN_LEVEL)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute("post-1", actor))
                .isInstanceOf(MembershipRequiredException.class);
    }

    @Test
    @DisplayName("멤버십 전용 포스트를 작성자 본인이 조회하면 성공한다")
    void execute_membersOnlyPost_author_returnsPostView() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        ActorContext actor = new ActorContext("artist-1", Set.of("ARTIST"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("post-1")).thenReturn(0L);
        when(reactionRepository.countByPostId("post-1")).thenReturn(0L);
        when(reactionRepository.find("post-1", "artist-1")).thenReturn(Optional.empty());
        when(accountProfileLookup.displayNameOf("artist-1")).thenReturn("Artist");

        PostView result = useCase.execute("post-1", actor);

        assertThat(result.visibility()).isEqualTo(PostVisibility.MEMBERS_ONLY);
    }

    @Test
    @DisplayName("본인이 리액션을 남긴 포스트 조회 시 myReaction 이 포함된다")
    void execute_postWithMyReaction_returnsMyReaction() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        Reaction myReaction = Reaction.create("post-1", "fan-1", "HEART");
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(commentRepository.countByPostId("post-1")).thenReturn(0L);
        when(reactionRepository.countByPostId("post-1")).thenReturn(1L);
        when(reactionRepository.find("post-1", "fan-1")).thenReturn(Optional.of(myReaction));
        when(accountProfileLookup.displayNameOf("artist-1")).thenReturn("Artist");

        PostView result = useCase.execute("post-1", actor);

        assertThat(result.myReaction()).isEqualTo("HEART");
    }
}
