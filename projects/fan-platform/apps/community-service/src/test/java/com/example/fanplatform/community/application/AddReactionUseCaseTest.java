package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AddReactionUseCaseTest {

    private static final String TENANT = "fan-platform";

    @Mock PostAccessGuard postAccessGuard;
    @Mock ReactionRepository reactionRepository;
    @Mock CommunityEventPublisher eventPublisher;

    @InjectMocks AddReactionUseCase useCase;

    @Test
    @DisplayName("같은 (post, reactor) 가 같은 reactionType 으로 두 번 호출 → save 와 outbox event 모두 skip (idempotent)")
    void idempotentSameReaction() {
        Post post = published();
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class))).thenReturn(post);
        Reaction existing = Reaction.create("p1", "fan-1", TENANT, ReactionType.LIKE);
        when(reactionRepository.find("p1", "fan-1", TENANT)).thenReturn(Optional.of(existing));
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(1L);

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        AddReactionUseCase.ReactionResult r = useCase.execute("p1", ReactionType.LIKE, actor);

        assertThat(r.totalReactions()).isEqualTo(1L);
        verify(reactionRepository, never()).save(any());

        // Same-type re-PUT is a true no-op: no outbox row should be appended
        // either, otherwise consumers see duplicate community.reaction.added
        // events with distinct event_ids that they cannot dedupe (the JPA
        // upsert produces no DB row change, so consumers have no way to know
        // the event is a redundancy).
        verify(eventPublisher, never()).publishReactionAdded(
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기존 reaction 과 다른 type → 변경 후 저장")
    void changeType() {
        Post post = published();
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class))).thenReturn(post);
        Reaction existing = Reaction.create("p1", "fan-1", TENANT, ReactionType.LIKE);
        when(reactionRepository.find("p1", "fan-1", TENANT)).thenReturn(Optional.of(existing));
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(1L);

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        useCase.execute("p1", ReactionType.LOVE, actor);

        assertThat(existing.getReactionType()).isEqualTo(ReactionType.LOVE);
        verify(reactionRepository).save(existing);
    }

    @Test
    @DisplayName("새 reaction → 신규 저장 + 이벤트 발행")
    void newReaction() {
        Post post = published();
        when(postAccessGuard.requirePublishedAccess(eq("p1"), any(ActorContext.class))).thenReturn(post);
        when(reactionRepository.find("p1", "fan-1", TENANT)).thenReturn(Optional.empty());
        when(reactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reactionRepository.countByPostId("p1", TENANT)).thenReturn(1L);

        ActorContext actor = new ActorContext("fan-1", TENANT, Set.of("FAN"));
        useCase.execute("p1", ReactionType.FIRE, actor);

        verify(reactionRepository).save(any(Reaction.class));
        verify(eventPublisher).publishReactionAdded(
                any(String.class), any(String.class), any(String.class),
                any(ReactionType.class), any());
    }

    private static Post published() {
        Post p = Post.createDraft("p1", TENANT, "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, "t", "b", null);
        p.publish(ActorType.AUTHOR);
        return p;
    }
}
