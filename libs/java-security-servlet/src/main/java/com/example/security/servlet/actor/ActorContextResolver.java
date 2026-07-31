package com.example.security.servlet.actor;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the current actor back off the Spring {@code SecurityContext} (ADR-MONO-058 § D1).
 *
 * <p>The caller supplies its own actor type, so the library stays free of any {@code projects/}
 * reference while the call site keeps a typed result.
 *
 * <p>Both failure messages are contractual, not cosmetic: consuming services map
 * {@link IllegalStateException} to <strong>422 {@code ILLEGAL_STATE}</strong>, and the promoted copies'
 * exact wording is what their tests and their operators read.
 */
public final class ActorContextResolver {

    private ActorContextResolver() {
    }

    /**
     * @param actorType the service's own actor type
     * @return the authenticated principal, cast to {@code actorType}
     * @throws IllegalStateException when nothing is authenticated, or when the principal is not an
     *         instance of {@code actorType} (e.g. a filter chain that installs a plain JWT principal —
     *         a workload-identity chain — rather than an actor)
     */
    public static <A> A currentOrThrow(Class<A> actorType) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated actor in SecurityContext");
        }
        Object principal = auth.getPrincipal();
        if (actorType.isInstance(principal)) {
            return actorType.cast(principal);
        }
        throw new IllegalStateException("Unexpected principal type: "
                + (principal == null ? "null" : principal.getClass().getName()));
    }
}
