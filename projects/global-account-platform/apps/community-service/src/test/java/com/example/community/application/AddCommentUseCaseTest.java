package com.example.community.application;

import com.example.community.application.event.CommunityEventPublisher;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.access.AccountProfileLookup;
import com.example.community.domain.comment.Comment;
import com.example.community.domain.comment.CommentRepository;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AddCommentUseCaseTest {

    @Mock PostAccessGuard postAccessGuard;
    @Mock CommentRepository commentRepository;
    @Mock AccountProfileLookup accountProfileLookup;
    @Mock CommunityEventPublisher eventPublisher;

    AddCommentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddCommentUseCase(postAccessGuard, commentRepository, accountProfileLookup, eventPublisher);
    }

    @Test
    @DisplayName("공개 게시물에 댓글 작성 시 저장되고 이벤트가 발행된다")
    void execute_publishedPost_savesCommentAndPublishesEvent() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "Title", "Body", null);
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        when(postAccessGuard.requirePublishedAccess("post-1", actor)).thenReturn(post);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountProfileLookup.displayNameOf("fan-1")).thenReturn("Fan Name");

        AddCommentUseCase.CommentView result = useCase.execute("post-1", "Great post!", actor);

        assertThat(result.postId()).isEqualTo("post-1");
        assertThat(result.authorAccountId()).isEqualTo("fan-1");
        assertThat(result.body()).isEqualTo("Great post!");
        assertThat(result.authorDisplayName()).isEqualTo("Fan Name");
        verify(eventPublisher).publishCommentCreated(any(), eq("post-1"), eq("artist-1"), eq("fan-1"), any());
    }

    @Test
    @DisplayName("존재하지 않는 게시물에 댓글 작성 시 PostNotFoundException 이 발생한다")
    void execute_postNotFound_throwsPostNotFoundException() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        when(postAccessGuard.requirePublishedAccess("missing-post", actor))
                .thenThrow(new PostNotFoundException("missing-post"));

        assertThatThrownBy(() -> useCase.execute("missing-post", "body", actor))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("댓글 작성 시 authorAccountId 가 actor 의 accountId 로 설정된다")
    void execute_validBody_commentAuthorMatchesActor() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        ActorContext actor = new ActorContext("member-2", Set.of("FAN"));

        when(postAccessGuard.requirePublishedAccess("post-2", actor)).thenReturn(post);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(accountProfileLookup.displayNameOf("member-2")).thenReturn("Member");

        AddCommentUseCase.CommentView result = useCase.execute("post-2", "Nice!", actor);

        assertThat(result.authorAccountId()).isEqualTo("member-2");
    }
}
