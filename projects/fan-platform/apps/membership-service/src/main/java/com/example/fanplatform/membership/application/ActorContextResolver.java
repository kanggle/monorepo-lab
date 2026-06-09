package com.example.fanplatform.membership.application;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current {@link ActorContext} from the Spring SecurityContext
 * (end-user routes). Kept in the application package so controllers depend on
 * the application layer, not directly on Spring Security types in presentation.
 */
public final class ActorContextResolver {

    private ActorContextResolver() {
    }

    public static ActorContext currentOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated actor in SecurityContext");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof ActorContext ctx) {
            return ctx;
        }
        throw new IllegalStateException("Unexpected principal type: "
                + (principal == null ? "null" : principal.getClass().getName()));
    }
}
