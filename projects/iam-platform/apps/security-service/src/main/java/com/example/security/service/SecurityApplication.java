package com.example.security.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * security-service entry point.
 *
 * <p><b>Why this class lives in {@code com.example.security.service} and not
 * {@code com.example.security} (TASK-BE-559).</b> {@code @SpringBootApplication}'s default
 * component-scan base package is the package this class is declared in. The shared library
 * {@code libs/java-security} owns {@code com.example.security} ({@code .access} / {@code .jwt} /
 * {@code .oauth2} / {@code .password} / {@code .pii} / {@code .redis}), so declaring the entry
 * point one level up would make this service scan the whole library tree — and any Spring bean
 * ever added to that library would silently auto-register into <em>this</em> service's context
 * and nowhere else, invisible to the compiler and to every unit test.
 *
 * <p>{@code com.example.security.service} is a <em>sibling</em> of the library packages, so the
 * default scan cannot reach them. <b>The isolation is the default scan rule itself — do not add
 * {@code scanBasePackages} or a separate {@code @ComponentScan} argument here</b>, and never
 * widen the scan back toward {@code com.example.security}.
 *
 * <p>See {@code specs/services/security-service/architecture.md} § Internal Structure Rule.
 */
@SpringBootApplication
@EnableScheduling
public class SecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityApplication.class, args);
    }
}
