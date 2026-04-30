package com.example.admin.presentation.aspect;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-028a guardrail: every controller mutation method
 * (@PostMapping/@PutMapping/@PatchMapping/@DeleteMapping under
 * com.example.admin.presentation.*) must declare @RequiresPermission.
 *
 * Per rbac.md D2 and task scope, missing-annotation deny-by-default at runtime
 * is deferred to TASK-BE-028b; this coverage test is the build-time guardrail
 * for the current increment.
 */
class AspectCoverageTest {

    private static final String BASE_PACKAGE = "com.example.admin.presentation";

    private static final List<Class<? extends Annotation>> MUTATION_MAPPINGS = List.of(
            PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    @Test
    void every_mutation_endpoint_declares_requires_permission() throws Exception {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory factory = new CachingMetadataReaderFactory(resolver);

        var resources = resolver.getResources(
                "classpath:com/example/admin/presentation/**/*.class");

        for (var r : resources) {
            MetadataReader mr = factory.getMetadataReader(r);
            String name = mr.getClassMetadata().getClassName();
            if (!name.startsWith(BASE_PACKAGE) || name.contains("$")) continue;

            Class<?> c;
            try { c = Class.forName(name); } catch (Throwable t) { continue; }
            if (c.getAnnotation(org.springframework.web.bind.annotation.RestController.class) == null
                    && c.getAnnotation(org.springframework.stereotype.Controller.class) == null) {
                continue;
            }

            for (Method m : c.getDeclaredMethods()) {
                boolean isMutation = MUTATION_MAPPINGS.stream()
                        .anyMatch(a -> m.isAnnotationPresent(a));
                if (!isMutation && m.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping rm = m.getAnnotation(RequestMapping.class);
                    isMutation = java.util.Arrays.stream(rm.method()).anyMatch(mh ->
                            mh.name().equals("POST") || mh.name().equals("PUT")
                                    || mh.name().equals("PATCH") || mh.name().equals("DELETE"));
                }
                if (!isMutation) continue;

                assertThat(m.isAnnotationPresent(RequiresPermission.class))
                        .as("Controller mutation method %s.%s must declare @RequiresPermission",
                                c.getSimpleName(), m.getName())
                        .isTrue();
            }
        }
    }
}
