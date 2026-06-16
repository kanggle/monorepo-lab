package com.example.community.application;

/**
 * Content access-policy constants shared by the post read use cases
 * ({@link GetPostUseCase}, {@code GetFeedUseCase}) and {@code PostAccessGuard}.
 *
 * <p>Previously the required plan level lived on {@link GetPostUseCase}, which forced
 * the feed use case and the access guard to reference a sibling use-case class purely
 * for a constant. Centralising it here removes that incidental coupling.
 */
final class AccessPolicy {

    private AccessPolicy() {
    }

    /** Plan level a fan must hold to view {@code MEMBERS_ONLY} content. */
    static final String REQUIRED_PLAN_LEVEL = "FAN_CLUB";
}
