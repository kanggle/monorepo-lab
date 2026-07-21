package com.example.fanplatform.membership.presentation.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a controller method's {@link com.example.fanplatform.membership.application.ActorContext}
 * parameter to the authenticated SecurityContext principal, centralizing the
 * "no authenticated actor → throw" path that was previously repeated inline as
 * {@code ActorContext actor = ActorContextResolver.currentOrThrow();} at every
 * controller method (TASK-FAN-BE-025 N1). Backed by
 * {@link CurrentActorArgumentResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentActor {
}
