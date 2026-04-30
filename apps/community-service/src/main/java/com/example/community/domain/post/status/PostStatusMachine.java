package com.example.community.domain.post.status;

import java.util.Map;
import java.util.Set;

/**
 * Post status state machine.
 *
 * <ul>
 *   <li>AUTHOR: DRAFT→PUBLISHED, PUBLISHED→HIDDEN, PUBLISHED→DELETED</li>
 *   <li>OPERATOR: PUBLISHED→HIDDEN, HIDDEN→PUBLISHED, *→DELETED</li>
 *   <li>DELETED is terminal: any transition from DELETED throws IllegalStateException.</li>
 * </ul>
 */
public final class PostStatusMachine {

    private static final Map<ActorType, Map<PostStatus, Set<PostStatus>>> TRANSITIONS = Map.of(
            ActorType.AUTHOR, Map.of(
                    PostStatus.DRAFT, Set.of(PostStatus.PUBLISHED),
                    PostStatus.PUBLISHED, Set.of(PostStatus.HIDDEN, PostStatus.DELETED)
            ),
            ActorType.OPERATOR, Map.of(
                    PostStatus.DRAFT, Set.of(PostStatus.DELETED),
                    PostStatus.PUBLISHED, Set.of(PostStatus.HIDDEN, PostStatus.DELETED),
                    PostStatus.HIDDEN, Set.of(PostStatus.PUBLISHED, PostStatus.DELETED)
            ),
            ActorType.SYSTEM, Map.of()
    );

    private PostStatusMachine() {
    }

    public static void ensureTransitionAllowed(PostStatus current, PostStatus target, ActorType actor) {
        if (current == PostStatus.DELETED) {
            throw new IllegalStateException("STATE_TRANSITION_INVALID");
        }
        if (current == target) {
            throw new IllegalStateException("STATE_TRANSITION_INVALID");
        }
        Set<PostStatus> allowed = TRANSITIONS
                .getOrDefault(actor, Map.of())
                .getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new IllegalStateException("STATE_TRANSITION_INVALID");
        }
    }

    public static boolean isTransitionAllowed(PostStatus current, PostStatus target, ActorType actor) {
        try {
            ensureTransitionAllowed(current, target, actor);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
