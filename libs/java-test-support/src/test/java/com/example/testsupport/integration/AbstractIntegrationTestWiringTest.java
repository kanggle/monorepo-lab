package com.example.testsupport.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Pins the wiring on {@link AbstractIntegrationTest} — TASK-MONO-546.
 *
 * <h2>Why a structural test, and not a behavioural one</h2>
 *
 * <p>The regression this guards is one CI cannot observe. {@code DockerAvailableCondition}'s
 * javadoc records the incident: evaluating a condition that has to invoke a static
 * method triggers class initialization, which runs this class's {@code static { }},
 * which fails with {@code ExceptionInInitializerError} on a host with no Docker
 * daemon. Delete the {@code @ExtendWith} line and that returns — but every CI runner
 * has Docker, so with and without the annotation produce the same green result there.
 * The failure only appears on a developer machine, which no gate watches.
 *
 * <p>So the annotation itself is the contract, and asserting it is the only thing
 * that can notice. These are pins: each one is checked against what
 * {@code AbstractIntegrationTest} and {@code DockerAvailableCondition} say in their
 * own javadoc, not against the current source merely because it is the current
 * source. If one of them ever needs to change, the javadoc changes with it.
 *
 * <h2>The load must not initialise</h2>
 *
 * <p>{@link AbstractIntegrationTest} starts MySQL and Kafka in a {@code static { }}
 * block, and this module's {@code test} task runs in the Docker-free
 * `build-and-test` lane. Reading annotations does not trigger initialization, but
 * relying on that silently is how it stops being true: the class is loaded here
 * through {@code Class.forName(name, false, loader)}, which states the requirement
 * in the code rather than in a comment.
 */
class AbstractIntegrationTestWiringTest {

    private static final String FQN = "com.example.testsupport.integration.AbstractIntegrationTest";

    private static Class<?> target;

    @BeforeAll
    static void loadWithoutInitialising() throws ClassNotFoundException {
        // initialize=false is the whole point — see the class javadoc.
        target = Class.forName(FQN, false, AbstractIntegrationTestWiringTest.class.getClassLoader());
    }

    @Test
    @DisplayName("is abstract — it is a base class, and an instantiable one would be collected as a test")
    void isAbstract() {
        assertThat(Modifier.isAbstract(target.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("carries @ExtendWith(DockerAvailableCondition) so every subclass SKIPS without Docker instead of erroring")
    void carriesTheDockerCondition() {
        ExtendWith extendWith = target.getAnnotation(ExtendWith.class);

        assertThat(extendWith)
                .as("without this, a Docker-less host gets ExceptionInInitializerError, not a skip")
                .isNotNull();
        assertThat(extendWith.value()).contains(DockerAvailableCondition.class);
    }

    @Test
    @DisplayName("carries @Tag(\"integration\") — the tag every Docker-free `check` excludes")
    void carriesTheIntegrationTag() {
        Tag tag = target.getAnnotation(Tag.class);

        assertThat(tag).isNotNull();
        // Not a cosmetic label: each service's build.gradle keeps `check` Docker-free by
        // excluding exactly this tag, and the integrationTest tasks select on it. A typo
        // here would move every inheriting suite out of both filters at once.
        assertThat(tag.value()).isEqualTo("integration");
    }

    @Test
    @DisplayName("sharedContainerProperties is static and @DynamicPropertySource — Spring ignores a non-static one")
    void registersSharedPropertiesStatically() throws NoSuchMethodException {
        Method method = target.getDeclaredMethod("sharedContainerProperties", DynamicPropertyRegistry.class);

        assertThat(method.getAnnotation(DynamicPropertySource.class))
                .as("subclass contexts get their MySQL/Kafka URLs from this method")
                .isNotNull();
        assertThat(Modifier.isStatic(method.getModifiers()))
                .as("Spring requires @DynamicPropertySource methods to be static")
                .isTrue();
    }

    @Test
    @DisplayName("MYSQL and KAFKA are protected static — subclasses reference the one shared instance")
    void exposesTheSharedContainersToSubclasses() throws NoSuchFieldException {
        for (String name : new String[] {"MYSQL", "KAFKA"}) {
            Field field = target.getDeclaredField(name);

            // static is the point of the class: one container per JVM outliving every
            // Spring context rebuild. protected is what lets a subclass reach it without
            // widening the surface to unrelated packages.
            assertThat(Modifier.isStatic(field.getModifiers())).as("%s is static", name).isTrue();
            assertThat(Modifier.isProtected(field.getModifiers())).as("%s is protected", name).isTrue();
        }
    }
}
