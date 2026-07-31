package com.example.fanplatform.membership.presentation.security;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.security.servlet.actor.AbstractCurrentActorArgumentResolver;
import org.springframework.stereotype.Component;

/**
 * Binds this service's {@link ActorContext} to {@code @CurrentActor} controller parameters.
 *
 * <p>The mechanism — the {@code @CurrentActor} annotation, the SecurityContext read and its
 * {@link IllegalStateException} failure path (hence the same 422 {@code ILLEGAL_STATE} mapping) — is
 * {@link AbstractCurrentActorArgumentResolver}'s since ADR-MONO-058 § D1. All this class contributes is
 * the one thing the library must not know: <em>which</em> actor type this service uses. It stays an
 * annotated {@code @Component} implementing {@code WebMvcConfigurer} so that {@code @WebMvcTest} slices
 * keep registering it (TASK-FAN-BE-025 N1).
 *
 * <p>Note the Order(1) {@code /internal/**} chain installs a plain JWT principal, not an
 * {@code ActorContext}; reaching an {@code @CurrentActor} parameter from there still fails loudly with
 * {@code "Unexpected principal type: …"} rather than resolving to null.
 */
@Component
public class CurrentActorArgumentResolver extends AbstractCurrentActorArgumentResolver<ActorContext> {

    public CurrentActorArgumentResolver() {
        super(ActorContext.class);
    }
}
