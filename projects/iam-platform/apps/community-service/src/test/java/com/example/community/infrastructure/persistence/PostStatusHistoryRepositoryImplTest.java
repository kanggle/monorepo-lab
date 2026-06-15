package com.example.community.infrastructure.persistence;

import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.domain.post.status.PostStatusHistoryEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostStatusHistoryRepositoryImpl 단위 테스트")
class PostStatusHistoryRepositoryImplTest {

    @Mock
    private PostStatusHistoryJpaRepository jpaRepository;

    @InjectMocks
    private PostStatusHistoryRepositoryImpl repository;

    @Test
    @DisplayName("save — 영속된 occurred_at 은 도메인 이벤트 시각(저장 시각 재스탬프 아님)")
    void save_persistsDomainOccurredAt_notSaveTime() {
        // An event time deliberately well in the past so a save-time re-stamp would differ.
        Instant eventTime = Instant.now().minus(1, ChronoUnit.HOURS);
        PostStatusHistoryEntry entry = new PostStatusHistoryEntry(
                "post-1", PostStatus.DRAFT, PostStatus.PUBLISHED,
                ActorType.AUTHOR, "author-1", "user-request", eventTime);

        repository.save(entry);

        ArgumentCaptor<PostStatusHistoryJpaEntity> captor =
                ArgumentCaptor.forClass(PostStatusHistoryJpaEntity.class);
        verify(jpaRepository).save(captor.capture());

        PostStatusHistoryJpaEntity persisted = captor.getValue();
        assertThat(persisted.getOccurredAt()).isEqualTo(eventTime);
        assertThat(persisted.getPostId()).isEqualTo("post-1");
        assertThat(persisted.getFromStatus()).isEqualTo("DRAFT");
        assertThat(persisted.getToStatus()).isEqualTo("PUBLISHED");
        assertThat(persisted.getActorType()).isEqualTo("AUTHOR");
    }
}
