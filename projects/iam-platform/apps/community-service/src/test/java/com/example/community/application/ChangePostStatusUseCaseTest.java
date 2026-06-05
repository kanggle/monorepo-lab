package com.example.community.application;

import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.domain.post.status.PostStatusHistoryEntry;
import com.example.community.domain.post.status.PostStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ChangePostStatusUseCaseTest {

    @Mock PostRepository postRepository;
    @Mock PostStatusHistoryRepository historyRepository;

    ChangePostStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChangePostStatusUseCase(postRepository, historyRepository);
    }

    @Test
    @DisplayName("DRAFT 포스트를 AUTHOR 가 PUBLISHED 로 전환하면 저장되고 히스토리가 기록된다")
    void execute_draftToPublishedByAuthor_savesPostAndHistory() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(post)).thenReturn(post);

        useCase.execute("post-1", PostStatus.PUBLISHED, ActorType.AUTHOR, "artist-1", null);

        verify(postRepository).save(post);
        verify(historyRepository).save(any(PostStatusHistoryEntry.class));
    }

    @Test
    @DisplayName("존재하지 않는 포스트 전환 요청 시 PostNotFoundException 이 발생한다")
    void execute_postNotFound_throwsPostNotFoundException() {
        when(postRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                useCase.execute("missing", PostStatus.PUBLISHED, ActorType.AUTHOR, "artist-1", null))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("허용되지 않은 상태 전환 시 IllegalStateException 이 전파된다")
    void execute_invalidTransition_throwsIllegalStateException() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));

        // DRAFT → HIDDEN 은 AUTHOR 에게 허용되지 않음
        assertThatThrownBy(() ->
                useCase.execute("post-1", PostStatus.HIDDEN, ActorType.AUTHOR, "artist-1", null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("OPERATOR 가 PUBLISHED 포스트를 HIDDEN 으로 전환하면 성공한다")
    void execute_publishedToHiddenByOperator_succeeds() {
        Post post = Post.createDraft("artist-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, "T", "B", null);
        post.publish(ActorType.AUTHOR);
        when(postRepository.findById("post-1")).thenReturn(Optional.of(post));
        when(postRepository.save(post)).thenReturn(post);

        useCase.execute("post-1", PostStatus.HIDDEN, ActorType.OPERATOR, "op-1", "violation");

        verify(postRepository).save(post);
        verify(historyRepository).save(any(PostStatusHistoryEntry.class));
    }
}
