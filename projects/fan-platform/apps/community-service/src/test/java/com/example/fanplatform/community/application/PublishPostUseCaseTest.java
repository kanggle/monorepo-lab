package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.post.status.PostStatusHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PublishPostUseCaseTest {

    @Mock PostRepository postRepository;
    @Mock PostStatusHistoryRepository historyRepository;
    @Mock CommunityEventPublisher eventPublisher;

    @Test
    @DisplayName("FAN_POST 발행 → DRAFT→PUBLISHED 전이 후 history + outbox 적재")
    void fanPostPublishesAndEmitsEvents() {
        PostMediaRefSerializer serializer = new PostMediaRefSerializer(new ObjectMapper());
        PublishPostUseCase useCase = new PublishPostUseCase(
                postRepository, historyRepository, eventPublisher, serializer);

        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext actor = new ActorContext("fan-1", "fan-platform", Set.of("FAN"));
        PostView view = useCase.execute(new PublishPostCommand(
                actor, PostType.FAN_POST, PostVisibility.PUBLIC,
                "title", "body", List.of("media-key-1")));

        assertThat(view.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(view.tenantId()).isEqualTo("fan-platform");
        assertThat(view.authorAccountId()).isEqualTo("fan-1");

        ArgumentCaptor<Post> savedPost = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(savedPost.capture());
        assertThat(savedPost.getValue().getTenantId()).isEqualTo("fan-platform");

        verify(historyRepository).save(any());
        verify(eventPublisher).publishPostPublished(
                anyString(), anyString(), anyString(),
                any(PostType.class), any(PostVisibility.class), any());
    }

    @Test
    @DisplayName("ARTIST_POST 발행 시 ARTIST role 미보유 → PermissionDeniedException")
    void artistPostRequiresArtistRole() {
        PostMediaRefSerializer serializer = new PostMediaRefSerializer(new ObjectMapper());
        PublishPostUseCase useCase = new PublishPostUseCase(
                postRepository, historyRepository, eventPublisher, serializer);

        ActorContext actor = new ActorContext("fan-1", "fan-platform", Set.of("FAN"));
        assertThatThrownBy(() -> useCase.execute(new PublishPostCommand(
                actor, PostType.ARTIST_POST, PostVisibility.PUBLIC,
                "title", "body", null)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("OPERATOR 는 ARTIST_POST 발행 가능")
    void operatorCanPublishArtistPost() {
        PostMediaRefSerializer serializer = new PostMediaRefSerializer(new ObjectMapper());
        PublishPostUseCase useCase = new PublishPostUseCase(
                postRepository, historyRepository, eventPublisher, serializer);

        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        ActorContext op = new ActorContext("ops-1", "fan-platform", Set.of("OPERATOR"));
        PostView view = useCase.execute(new PublishPostCommand(
                op, PostType.ARTIST_POST, PostVisibility.MEMBERS_ONLY,
                "t", "b", null));
        assertThat(view.status()).isEqualTo(PostStatus.PUBLISHED);
    }
}
