package com.example.fanplatform.community.domain.post.status;

import java.util.Map;
import java.util.Set;

/**
 * Post status state machine. Stateless utility — every transition flows through
 * {@link #ensureTransitionAllowed} so business logic cannot bypass the rules.
 *
 * <p>Transition matrix (refer to TASK-FAN-BE-002 § In Scope #3):
 * <ul>
 *   <li>AUTHOR: DRAFT &rarr; PUBLISHED, PUBLISHED &rarr; HIDDEN, PUBLISHED &rarr; DELETED</li>
 *   <li>OPERATOR (admin): DRAFT &rarr; DELETED, PUBLISHED &rarr; HIDDEN, PUBLISHED &rarr; DELETED,
 *       HIDDEN &rarr; PUBLISHED, HIDDEN &rarr; DELETED</li>
 *   <li>SYSTEM: no autonomous transitions in v1</li>
 * </ul>
 *
 * <p>{@link PostStatus#DELETED} is terminal — every transition out of DELETED is
 * forbidden regardless of actor. Self-transition (PUBLISHED &rarr; PUBLISHED) is
 * also forbidden so callers cannot silently no-op a status change.
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
            throw new InvalidStateTransitionException(current, target, actor);
        }
        if (current == target) {
            throw new InvalidStateTransitionException(current, target, actor);
        }
        Set<PostStatus> allowed = TRANSITIONS
                .getOrDefault(actor, Map.of())
                .getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStateTransitionException(current, target, actor);
        }
    }

    public static boolean isTransitionAllowed(PostStatus current, PostStatus target, ActorType actor) {
        try {
            ensureTransitionAllowed(current, target, actor);
            return true;
        } catch (InvalidStateTransitionException e) {
            return false;
        }
    }
}
