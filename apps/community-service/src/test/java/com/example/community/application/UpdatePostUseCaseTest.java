package com.example.community.application;

import com.example.community.application.exception.PermissionDeniedException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class UpdatePostUseCaseTest {

    @Mock PostRepository postRepository;

    UpdatePostUseCase useCase;

    @BeforeEach
    void setUp() {
        PostMediaUrlsSerializer serializer = new PostMediaUrlsSerializer(new ObjectMapper());
        useCase = new UpdatePostUseCase(postRepository, serializer);
    }

    @Test
    @DisplayName("작성자 본인이 포스트를 수정하면 제목/본문/미디어가 갱신되어 저장된다")
    void execute_authorUpdatesPost_contentIsUpdated() {
        String authorId = "artist-1";
        Post existing = Post.createDraft(
                authorId,
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "old-title",
                "old-body",
                null
        );
        existing.publish(com.example.community.domain.post.status.ActorType.AUTHOR);
        when(postRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext(authorId, Set.of("ARTIST"));
        useCase.execute(
                existing.getId(),
                actor,
                "new-title",
                "new-body",
                List.of("https://cdn.example.com/a.png")
        );

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        Post saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("new-title");
        assertThat(saved.getBody()).isEqualTo("new-body");
        assertThat(saved.getMediaUrlsJson()).isEqualTo("[\"https://cdn.example.com/a.png\"]");
    }

    @Test
    @DisplayName("비저자가 포스트를 수정하려 하면 PermissionDeniedException 이 발생한다")
    void execute_nonAuthorUpdatesPost_throwsPermissionDenied() {
        String authorId = "artist-1";
        Post existing = Post.createDraft(
                authorId,
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "title",
                "body",
                null
        );
        existing.publish(com.example.community.domain.post.status.ActorType.AUTHOR);
        when(postRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        ActorContext nonAuthor = new ActorContext("fan-1", Set.of("FAN"));

        assertThatThrownBy(() -> useCase.execute(
                existing.getId(),
                nonAuthor,
                "new-title",
                "new-body",
                List.of()
        )).isInstanceOf(PermissionDeniedException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("존재하지 않는 포스트 ID 로 수정하면 PostNotFoundException 이 발생한다")
    void execute_nonExistentPost_throwsPostNotFound() {
        String missingId = "missing-post-id";
        when(postRepository.findById(missingId)).thenReturn(Optional.empty());

        ActorContext actor = new ActorContext("artist-1", Set.of("ARTIST"));

        assertThatThrownBy(() -> useCase.execute(
                missingId,
                actor,
                "title",
                "body",
                List.of()
        )).isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("mediaUrls 가 null 이면 mediaUrlsJson 은 null 로 직렬화된다")
    void execute_nullMediaUrls_mediaUrlsJsonIsNull() {
        String authorId = "artist-1";
        Post existing = Post.createDraft(
                authorId,
                PostType.ARTIST_POST,
                PostVisibility.PUBLIC,
                "old-title",
                "old-body",
                "[\"https://cdn.example.com/old.png\"]"
        );
        existing.publish(com.example.community.domain.post.status.ActorType.AUTHOR);
        when(postRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext(authorId, Set.of("ARTIST"));
        useCase.execute(existing.getId(), actor, "new-title", "new-body", null);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getMediaUrlsJson()).isNull();
    }
}
