package com.example.fanplatform.notification.presentation.security;

import com.example.fanplatform.notification.application.ActorContext;
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
 */
@Component
public class CurrentActorArgumentResolver extends AbstractCurrentActorArgumentResolver<ActorContext> {

    public CurrentActorArgumentResolver() {
        super(ActorContext.class);
    }
}
