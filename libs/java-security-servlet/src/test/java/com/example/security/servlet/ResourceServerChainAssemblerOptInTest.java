package com.example.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The constraint {@code ADR-MONO-058 § D4} states in the same breath as the promotion itself, and that
 * {@code platform/shared-library-policy.md} § <em>No context-wide annotations in a shared
 * {@code @AutoConfiguration}</em> makes a rule: <strong>this assembly must be opt-in.</strong>
 *
 * <p>A javadoc sentence saying so is not a guard. These assertions are, because the failure they exist
 * for is invisible to every other kind of test: an auto-configuration installs itself in a consumer's
 * context, so no compiler, no unit test and no slice test observes it — only a booting application
 * does, and by then the library has already changed that application's authentication path.
 *
 * <p>The same reasoning is why the negative assertions here are paired with a <em>positive control</em>:
 * a query that finds nothing because it was asked wrongly reads exactly like a clean result.
 */
@DisplayName("ResourceServerChainAssembler — opt-in only, never auto-configured")
class ResourceServerChainAssemblerOptInTest {

    private static final String AUTOCONFIG_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String SPRING_FACTORIES = "META-INF/spring.factories";

    private static List<String> springAnnotationsOn(Class<?> type) {
        List<String> found = new ArrayList<>();
        for (Annotation annotation : type.getAnnotations()) {
            String name = annotation.annotationType().getName();
            if (name.startsWith("org.springframework")) {
                found.add(name);
            }
        }
        return found;
    }

    /**
     * URLs for {@code name} that come from a directory rather than a jar. Everything this module owns
     * is a directory on the test classpath ({@code build/classes/…}, {@code build/resources/…}); every
     * dependency is a jar. So a directory hit for a registration file means <em>this module</em> ships
     * one.
     */
    private static List<URL> directoryClasspathHits(String name) throws Exception {
        List<URL> hits = new ArrayList<>();
        for (URL url : Collections.list(
                ResourceServerChainAssembler.class.getClassLoader().getResources(name))) {
            if (!url.toString().contains(".jar!")) {
                hits.add(url);
            }
        }
        return hits;
    }

    @Test
    @DisplayName("the class carries no Spring annotation at all — no @Configuration, no stereotype")
    void classCarriesNoSpringAnnotation() {
        assertThat(springAnnotationsOn(ResourceServerChainAssembler.class))
                .as("a shared class that annotates itself into every consumer's context is exactly "
                    + "what § D4 forbids")
                .isEmpty();
    }

    @Test
    @DisplayName("neither nested builder carries a Spring annotation either")
    void buildersCarryNoSpringAnnotation() {
        assertThat(springAnnotationsOn(ResourceServerChainAssembler.JwtDecoderBuilder.class)).isEmpty();
        assertThat(springAnnotationsOn(ResourceServerChainAssembler.FilterChainBuilder.class)).isEmpty();
    }

    @Test
    @DisplayName("no method anywhere in the class is a @Bean factory method")
    void noBeanFactoryMethods() {
        List<Class<?>> types = List.of(
                ResourceServerChainAssembler.class,
                ResourceServerChainAssembler.JwtDecoderBuilder.class,
                ResourceServerChainAssembler.FilterChainBuilder.class);

        List<String> annotated = new ArrayList<>();
        for (Class<?> type : types) {
            for (var method : type.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    if (annotation.annotationType().getName().startsWith("org.springframework")) {
                        annotated.add(type.getSimpleName() + "#" + method.getName()
                                      + " -> " + annotation.annotationType().getName());
                    }
                }
            }
        }
        assertThat(annotated)
                .as("bean declaration belongs at the consuming service's wiring site, in the open")
                .isEmpty();
    }

    @Test
    @DisplayName("this module registers no auto-configuration — and the query is proven able to find one")
    void moduleShipsNoAutoConfigurationRegistration() throws Exception {
        // Positive control FIRST. An empty result from a classpath query is only evidence when the
        // query has been shown to return something when the thing exists: spring-boot-autoconfigure
        // ships this exact file, so a zero total would mean the lookup is broken, not that the module
        // is clean.
        List<URL> allHits = Collections.list(
                ResourceServerChainAssembler.class.getClassLoader().getResources(AUTOCONFIG_IMPORTS));
        assertThat(allHits)
                .as("positive control: at least one dependency must ship an AutoConfiguration.imports, "
                    + "otherwise this test's zero-finding proves nothing")
                .isNotEmpty();

        assertThat(directoryClasspathHits(AUTOCONFIG_IMPORTS))
                .as("libs/java-security-servlet must ship no auto-configuration registration")
                .isEmpty();
    }

    @Test
    @DisplayName("this module registers nothing via the legacy spring.factories mechanism either")
    void moduleShipsNoSpringFactories() throws Exception {
        assertThat(directoryClasspathHits(SPRING_FACTORIES))
                .as("the pre-3.0 registration file is a second door into every consumer's context")
                .isEmpty();
    }
}
