package com.example.fanplatform.community.domain.post.status;

/**
 * Thrown when a post status transition is rejected by {@link PostStatusMachine}.
 *
 * <p>Mapped to HTTP 422 {@code POST_STATUS_TRANSITION_INVALID} by the controller
 * advice. Carries the {@code from} and {@code to} so the API layer can surface
 * a useful error message.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final PostStatus from;
    private final PostStatus to;
    private final ActorType actor;

    public InvalidStateTransitionException(PostStatus from, PostStatus to, ActorType actor) {
        super("Invalid post status transition: " + from + " -> " + to + " by " + actor);
        this.from = from;
        this.to = to;
        this.actor = actor;
    }

    public PostStatus from() { return from; }
    public PostStatus to() { return to; }
    public ActorType actor() { return actor; }
}
