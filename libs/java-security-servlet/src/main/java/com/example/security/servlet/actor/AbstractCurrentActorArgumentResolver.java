package com.example.security.servlet.actor;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Objects;

/**
 * Resolves {@code @CurrentActor <ActorType>} controller parameters by delegating to
 * {@link ActorContextResolver#currentOrThrow(Class)} (ADR-MONO-058 § D1), and registers itself as an
 * argument resolver via {@link WebMvcConfigurer}.
 *
 * <h2>Opt-in by design — no {@code @Component} here</h2>
 *
 * This class is deliberately <strong>not</strong> a Spring bean. A shared library must not install a
 * context-wide component in every consumer (`platform/shared-library-policy.md § No context-wide
 * annotations`) — several consumers of this module have no actor concept at all. Each service that wants
 * the binding declares one small subclass naming its own actor type:
 *
 * <pre>{@code
 * @Component
 * public class CurrentActorArgumentResolver
 *         extends AbstractCurrentActorArgumentResolver<ActorContext> {
 *     public CurrentActorArgumentResolver() {
 *         super(ActorContext.class);
 *     }
 * }
 * }</pre>
 *
 * <p>Keeping it an annotated {@code @Component} that implements {@link WebMvcConfigurer} is also what
 * makes it visible inside a {@code @WebMvcTest} slice, which registers exactly those web-layer
 * component types. A {@code @Bean} in a plain {@code @Configuration} would not be picked up, and every
 * slice test would silently lose {@code @CurrentActor} binding.
 *
 * <p>The "no authenticated actor" failure path — its exact {@link IllegalStateException} type and
 * message, hence the consuming service's 422 {@code ILLEGAL_STATE} mapping — is
 * {@link ActorContextResolver}'s and is unchanged by this indirection.
 *
 * @param <A> the service's own actor type
 */
public abstract class AbstractCurrentActorArgumentResolver<A>
        implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    private final Class<A> actorType;

    protected AbstractCurrentActorArgumentResolver(Class<A> actorType) {
        this.actorType = Objects.requireNonNull(actorType, "actorType");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class)
                && actorType.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return ActorContextResolver.currentOrThrow(actorType);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }
}
