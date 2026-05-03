package com.example.fanplatform.community.application.exception;

import com.example.fanplatform.community.domain.post.PostVisibility;

/**
 * Thrown when the actor does not hold the membership tier required to read a
 * gated post. Mapped to HTTP 403 {@code MEMBERSHIP_REQUIRED}.
 */
public class MembershipRequiredException extends RuntimeException {

    private final PostVisibility requiredTier;

    public MembershipRequiredException(PostVisibility requiredTier) {
        super("Membership required: " + requiredTier);
        this.requiredTier = requiredTier;
    }

    public PostVisibility requiredTier() {
        return requiredTier;
    }
}
