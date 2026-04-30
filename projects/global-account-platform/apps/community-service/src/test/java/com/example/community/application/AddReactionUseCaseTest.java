package com.example.community.application;

import com.example.community.application.event.CommunityEventPublisher;
import com.example.community.application.exception.PostNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AddReactionUseCaseTest {

    @Mock PostAccessGuard postAccessGuard;
    @Mock ReactionRepository reactionRepository;
    @Mock CommunityEventPublisher eventPublisher;

    AddReactionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AddReactionUseCase(postAccessGuard, reactionRepository, eventPublisher);
    }

    @Test
    @DisplayName("처음 리액션 추가 시 저장되고 isNew=true 로 이벤트가 발행된다")
    void execute_newReaction_savesAndPublishesWithIsNewTrue() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        when(postAccessGuard.requirePublishedAccess("post-1", actor)).thenReturn(null);
        when(reactionRepository.find("post-1", "fan-1")).thenReturn(Optional.empty());
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reactionRepository.countByPostId("post-1")).thenReturn(5L);

        AddReactionUseCase.ReactionResult result = useCase.execute("post-1", "HEART", actor);

        assertThat(result.emojiCode()).isEqualTo("HEART");
        assertThat(result.totalReactions()).isEqualTo(5L);
        verify(eventPublisher).publishReactionAdded(eq("post-1"), eq("fan-1"), eq("HEART"), eq(true), any());
    }

    @Test
    @DisplayName("동일 이모지로 재요청 시 저장이 발생하지 않는다")
    void execute_sameEmoji_noSave() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        Reaction existing = Reaction.create("post-1", "fan-1", "HEART");
        when(postAccessGuard.requirePublishedAccess("post-1", actor)).thenReturn(null);
        when(reactionRepository.find("post-1", "fan-1")).thenReturn(Optional.of(existing));
        when(reactionRepository.countByPostId("post-1")).thenReturn(3L);

        AddReactionUseCase.ReactionResult result = useCase.execute("post-1", "HEART", actor);

        assertThat(result.emojiCode()).isEqualTo("HEART");
        verify(reactionRepository, never()).save(any());
        verify(eventPublisher).publishReactionAdded(eq("post-1"), eq("fan-1"), eq("HEART"), eq(false), any());
    }

    @Test
    @DisplayName("다른 이모지로 변경 시 changeEmoji 가 호출되고 저장된다")
    void execute_differentEmoji_changesEmojiAndSaves() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        Reaction existing = Reaction.create("post-1", "fan-1", "HEART");
        when(postAccessGuard.requirePublishedAccess("post-1", actor)).thenReturn(null);
        when(reactionRepository.find("post-1", "fan-1")).thenReturn(Optional.of(existing));
        when(reactionRepository.save(existing)).thenReturn(existing);
        when(reactionRepository.countByPostId("post-1")).thenReturn(3L);

        AddReactionUseCase.ReactionResult result = useCase.execute("post-1", "FIRE", actor);

        assertThat(result.emojiCode()).isEqualTo("FIRE");
        assertThat(existing.getEmojiCode()).isEqualTo("FIRE");
        verify(reactionRepository).save(existing);
        verify(eventPublisher).publishReactionAdded(eq("post-1"), eq("fan-1"), eq("FIRE"), eq(false), any());
    }

    @Test
    @DisplayName("지원하지 않는 emojiCode 입력 시 IllegalArgumentException 이 발생한다")
    void execute_unsupportedEmojiCode_throwsIllegalArgumentException() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        assertThatThrownBy(() -> useCase.execute("post-1", "THUMBSUP", actor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("THUMBSUP");
    }

    @Test
    @DisplayName("null emojiCode 입력 시 IllegalArgumentException 이 발생한다")
    void execute_nullEmojiCode_throwsIllegalArgumentException() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));

        assertThatThrownBy(() -> useCase.execute("post-1", null, actor))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("게시물 접근 불가 시 예외가 전파된다")
    void execute_postNotFound_throwsPostNotFoundException() {
        ActorContext actor = new ActorContext("fan-1", Set.of("FAN"));
        when(postAccessGuard.requirePublishedAccess("post-x", actor))
                .thenThrow(new PostNotFoundException("post-x"));

        assertThatThrownBy(() -> useCase.execute("post-x", "HEART", actor))
                .isInstanceOf(PostNotFoundException.class);
    }
}
