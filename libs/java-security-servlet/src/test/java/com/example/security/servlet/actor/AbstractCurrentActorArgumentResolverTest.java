package com.example.security.servlet.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

/**
 * The promoted {@code @CurrentActor} argument-resolver plumbing (ADR-MONO-058 § D1).
 *
 * <p>Covers the type-matching matrix and the self-registration; the "does it actually get registered by
 * Spring" question is a wiring question and is answered per service by that service's own auth-path
 * slice test against the real filter chain, not here.
 */
@DisplayName("AbstractCurrentActorArgumentResolver — @CurrentActor parameter binding, mechanism only")
class AbstractCurrentActorArgumentResolverTest {

    record TestActor(String accountId, String tenantId, Set<String> roles) {
    }

    record OtherActor(String accountId) {
    }

    /** The four-line subclass every consuming service writes. */
    static final class TestActorArgumentResolver extends AbstractCurrentActorArgumentResolver<TestActor> {
        TestActorArgumentResolver() {
            super(TestActor.class);
        }
    }

    @SuppressWarnings("unused")
    static final class Controller {
        void annotatedAndAssignable(@CurrentActor TestActor actor) {
        }

        void annotatedButWrongType(@CurrentActor OtherActor actor) {
        }

        void assignableButNotAnnotated(TestActor actor) {
        }

        void annotatedString(@CurrentActor String actor) {
        }
    }

    private final TestActorArgumentResolver resolver = new TestActorArgumentResolver();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static MethodParameter param(String methodName, Class<?> parameterType) {
        Method method;
        try {
            method = Controller.class.getDeclaredMethod(methodName, parameterType);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return new MethodParameter(method, 0);
    }

    @Test
    @DisplayName("supports a parameter that is both @CurrentActor-annotated and of the actor type")
    void supportsAnnotatedAndAssignable() {
        assertThat(resolver.supportsParameter(param("annotatedAndAssignable", TestActor.class))).isTrue();
    }

    @Test
    @DisplayName("does NOT support @CurrentActor on another service's actor type")
    void rejectsWrongActorType() {
        assertThat(resolver.supportsParameter(param("annotatedButWrongType", OtherActor.class))).isFalse();
    }

    @Test
    @DisplayName("does NOT support the actor type without the annotation")
    void rejectsUnannotated() {
        assertThat(resolver.supportsParameter(param("assignableButNotAnnotated", TestActor.class))).isFalse();
    }

    @Test
    @DisplayName("does NOT support @CurrentActor on an unrelated type")
    void rejectsUnrelatedAnnotatedType() {
        assertThat(resolver.supportsParameter(param("annotatedString", String.class))).isFalse();
    }

    @Test
    @DisplayName("resolves to the SecurityContext actor")
    void resolvesFromSecurityContext() {
        TestActor actor = new TestActor("acc-1", "tenant-x", Set.of("ALPHA"));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("acc-1")
                .claim("tenant_id", "tenant-x")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new ActorAuthenticationToken(jwt, actor, "acc-1", List.of()));

        Object resolved = resolver.resolveArgument(
                param("annotatedAndAssignable", TestActor.class), null, null, null);

        assertThat(resolved).isSameAs(actor);
    }

    @Test
    @DisplayName("no authenticated actor -> the contractual IllegalStateException (mapped to 422 by consumers)")
    void unauthenticatedThrows() {
        SecurityContextHolder.clearContext();

        MethodParameter parameter = param("annotatedAndAssignable", TestActor.class);
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No authenticated actor in SecurityContext");
    }

    @Test
    @DisplayName("registers itself through WebMvcConfigurer#addArgumentResolvers")
    void selfRegisters() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        resolver.addArgumentResolvers(resolvers);

        assertThat(resolvers).containsExactly(resolver);
    }

    @Test
    @DisplayName("a null actor type is rejected at construction")
    void nullActorTypeRejected() {
        assertThatThrownBy(() -> new AbstractCurrentActorArgumentResolver<TestActor>(null) {
        }).isInstanceOf(NullPointerException.class);
    }
}
