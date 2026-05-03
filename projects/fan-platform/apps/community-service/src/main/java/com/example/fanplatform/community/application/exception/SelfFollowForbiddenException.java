package com.example.fanplatform.community.application.exception;

/**
 * Thrown when an account attempts to follow itself.
 * Mapped to HTTP 422 {@code SELF_FOLLOW_FORBIDDEN}.
 */
public class SelfFollowForbiddenException extends RuntimeException {
    public SelfFollowForbiddenException() {
        super("SELF_FOLLOW_FORBIDDEN");
    }
}
