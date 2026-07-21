package com.example.fanplatform.artist.adapter.in.web.security;

import com.example.fanplatform.artist.application.ActorContext;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Resolves {@code @CurrentActor ActorContext} controller parameters by delegating
 * to {@link ActorContextResolver#currentOrThrow()}, so the "no actor" failure path
 * — its exact {@link IllegalStateException} type/message, hence the same 422
 * ({@code ILLEGAL_STATE}) mapping — is byte-identical to the pre-refactor inline
 * call (TASK-FAN-BE-025 N1). Registers itself as an argument resolver via
 * {@link WebMvcConfigurer}.
 */
@Component
public class CurrentActorArgumentResolver
        implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class)
                && ActorContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return ActorContextResolver.currentOrThrow();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }
}
