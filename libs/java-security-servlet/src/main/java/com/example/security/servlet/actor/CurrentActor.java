package com.example.security.servlet.actor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller method parameter to the authenticated actor held in the {@code SecurityContext}
 * (ADR-MONO-058 § D1), centralising the "no authenticated actor → throw" path instead of repeating
 * {@code ActorContextResolver.currentOrThrow(...)} inline at every controller method.
 *
 * <p>Backed by {@link AbstractCurrentActorArgumentResolver}, which each service subclasses once —
 * naming its own actor type — and registers as a {@code @Component}. The annotation itself is inert
 * without that registration.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentActor {
}
