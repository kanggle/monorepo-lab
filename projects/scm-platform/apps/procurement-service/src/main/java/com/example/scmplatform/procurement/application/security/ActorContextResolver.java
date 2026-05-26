package com.example.scmplatform.procurement.application.security;

import com.example.scmplatform.procurement.application.ActorContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves the current {@link ActorContext} from the Spring {@link SecurityContextHolder}.
 *
 * <p>Moved from {@code infrastructure/security/} to {@code application/security/} so that
 * {@code presentation/controller/} can call it without crossing the
 * presentation → infrastructure forbidden dependency boundary
 * (TASK-SCM-BE-017 A2 — layer hygiene).
 *
 * <p>Static helper — not a Spring bean. Call {@link #currentOrThrow()} inside controller
 * methods after the security filter chain has populated the authentication.
 */
public final class ActorContextResolver {

    private ActorContextResolver() {
    }

    /**
     * Returns the {@link ActorContext} for the current authenticated principal.
     *
     * @throws IllegalStateException if there is no authenticated principal or the
     *         principal is not an {@link ActorContext} instance
     */
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
