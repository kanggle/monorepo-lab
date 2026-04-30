package com.example.community.domain.post.status;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void author_draft_to_deleted_not_allowed() {
        // Authors cannot go directly from DRAFT to DELETED — must publish first then delete.
        assertThat(PostStatusMachine.isTransitionAllowed(
                PostStatus.DRAFT, PostStatus.DELETED, ActorType.AUTHOR)).isFalse();
        assertThatThrownBy(() ->
                PostStatusMachine.ensureTransitionAllowed(PostStatus.DRAFT, PostStatus.DELETED, ActorType.AUTHOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STATE_TRANSITION_INVALID");
    }

    @Test
    void author_hidden_to_published_not_allowed() {
        assertThatThrownBy(() ->
                PostStatusMachine.ensureTransitionAllowed(PostStatus.HIDDEN, PostStatus.PUBLISHED, ActorType.AUTHOR))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("STATE_TRANSITION_INVALID");
    }

    @Test
    void operator_hidden_to_published_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.HIDDEN, PostStatus.PUBLISHED, ActorType.OPERATOR);
    }

    @Test
    void operator_published_to_hidden_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.PUBLISHED, PostStatus.HIDDEN, ActorType.OPERATOR);
    }

    @Test
    void operator_star_to_deleted_allowed() {
        PostStatusMachine.ensureTransitionAllowed(PostStatus.DRAFT, PostStatus.DELETED, ActorType.OPERATOR);
        PostStatusMachine.ensureTransitionAllowed(PostStatus.PUBLISHED, PostStatus.DELETED, ActorType.OPERATOR);
        PostStatusMachine.ensureTransitionAllowed(PostStatus.HIDDEN, PostStatus.DELETED, ActorType.OPERATOR);
    }

    @Test
    void deleted_to_anything_forbidden_for_all_actors() {
        for (ActorType actor : ActorType.values()) {
            for (PostStatus target : PostStatus.values()) {
                if (target == PostStatus.DELETED) continue;
                assertThat(PostStatusMachine.isTransitionAllowed(PostStatus.DELETED, target, actor))
                        .as("DELETED -> %s (%s) must be forbidden", target, actor)
                        .isFalse();
            }
        }
    }

    @Test
    void same_state_noop_is_forbidden() {
        assertThat(PostStatusMachine.isTransitionAllowed(
                PostStatus.PUBLISHED, PostStatus.PUBLISHED, ActorType.AUTHOR)).isFalse();
    }
}
