package com.example.fanplatform.community.domain.post.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostStatusMachine — fan-platform")
class PostStatusMachineTest {

    @Test
    void author_draft_to_published_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.DRAFT, PostStatus.PUBLISHED, ActorType.AUTHOR);
    }

    @Test
    void author_published_to_hidden_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.PUBLISHED, PostStatus.HIDDEN, ActorType.AUTHOR);
    }

    @Test
    void author_published_to_deleted_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.PUBLISHED, PostStatus.DELETED, ActorType.AUTHOR);
    }

    @Test
    @DisplayName("PUBLISHED → DRAFT 는 모든 actor 에 대해 거부 (AC: 422 reject)")
    void published_to_draft_forbidden_for_all_actors() {
        for (ActorType actor : ActorType.values()) {
            assertThat(PostStatusMachine.isTransitionAllowed(
                    PostStatus.PUBLISHED, PostStatus.DRAFT, actor))
                    .as("PUBLISHED → DRAFT must be forbidden for %s", actor)
                    .isFalse();
        }
        assertThatThrownBy(() -> PostStatusMachine.ensureTransitionAllowed(
                PostStatus.PUBLISHED, PostStatus.DRAFT, ActorType.AUTHOR))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void author_hidden_to_published_not_allowed() {
        assertThatThrownBy(() -> PostStatusMachine.ensureTransitionAllowed(
                PostStatus.HIDDEN, PostStatus.PUBLISHED, ActorType.AUTHOR))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void operator_hidden_to_published_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.HIDDEN, PostStatus.PUBLISHED, ActorType.OPERATOR);
    }

    @Test
    void operator_can_delete_from_anywhere_except_deleted() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.DRAFT, PostStatus.DELETED, ActorType.OPERATOR);
        PostStatusMachine.ensureTransitionAllowed(PostStatus.PUBLISHED, PostStatus.DELETED, ActorType.OPERATOR);
        PostStatusMachine.ensureTransitionAllowed(PostStatus.HIDDEN, PostStatus.DELETED, ActorType.OPERATOR);
    }

    @Test
    void deleted_is_terminal_for_all_actors() {
        for (ActorType actor : ActorType.values()) {
            for (PostStatus target : PostStatus.values()) {
                if (target == PostStatus.DELETED) continue;
                assertThat(PostStatusMachine.isTransitionAllowed(
                        PostStatus.DELETED, target, actor))
                        .as("DELETED → %s (%s) must be forbidden", target, actor)
                        .isFalse();
            }
        }
    }

    @Test
    void same_state_transition_is_forbidden() {
        for (PostStatus s : PostStatus.values()) {
            for (ActorType actor : ActorType.values()) {
                assertThat(PostStatusMachine.isTransitionAllowed(s, s, actor))
                        .as("self-transition %s for %s must be forbidden", s, actor)
                        .isFalse();
            }
        }
    }

    @Test
    void author_cannot_skip_publish() {
        // Author cannot delete a DRAFT directly — must publish first.
        assertThat(PostStatusMachine.isTransitionAllowed(
                PostStatus.DRAFT, PostStatus.DELETED, ActorType.AUTHOR)).isFalse();
        // Author cannot hide a DRAFT either.
        assertThat(PostStatusMachine.isTransitionAllowed(
                PostStatus.DRAFT, PostStatus.HIDDEN, ActorType.AUTHOR)).isFalse();
    }

    @Test
    void exception_carries_from_to_actor() {
        try {
            PostStatusMachine.ensureTransitionAllowed(
                    PostStatus.PUBLISHED, PostStatus.DRAFT, ActorType.AUTHOR);
        } catch (InvalidStateTransitionException e) {
            assertThat(e.from()).isEqualTo(PostStatus.PUBLISHED);
            assertThat(e.to()).isEqualTo(PostStatus.DRAFT);
            assertThat(e.actor()).isEqualTo(ActorType.AUTHOR);
        }
    }
}
