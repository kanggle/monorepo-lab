package com.example.community.infrastructure.security;

import com.example.community.application.ActorContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ActorContextResolver {

    private ActorContextResolver() {}

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
