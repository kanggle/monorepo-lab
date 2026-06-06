package com.example.auth.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;

/**
 * Refuses to boot when JWT key paths still resolve to bundled classpath
 * resources in a non-development profile. Production deployments must inject
 * keys from a secrets store at runtime via JWT_PRIVATE_KEY_PATH /
 * JWT_PUBLIC_KEY_PATH pointing at filesystem paths.
 */
@Component
public class JwtKeyPathValidator {

    static final Set<String> ALLOWED_CLASSPATH_PROFILES = Set.of("local", "test", "integration-test", "e2e");
    private static final String CLASSPATH_PREFIX = "classpath:";

    private final Environment environment;
    private final String privateKeyPath;
    private final String publicKeyPath;

    public JwtKeyPathValidator(Environment environment,
                               @Value("${auth.jwt.private-key-path}") String privateKeyPath,
                               @Value("${auth.jwt.public-key-path}") String publicKeyPath) {
        this.environment = environment;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
    }

    @PostConstruct
    void validate() {
        if (isDevelopmentProfile()) {
            return;
        }
        if (isClasspathPath(privateKeyPath)) {
            throw new IllegalStateException(rejection("JWT_PRIVATE_KEY_PATH", "auth.jwt.private-key-path", privateKeyPath));
        }
        if (isClasspathPath(publicKeyPath)) {
            throw new IllegalStateException(rejection("JWT_PUBLIC_KEY_PATH", "auth.jwt.public-key-path", publicKeyPath));
        }
    }

    private boolean isDevelopmentProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return false;
        }
        return Arrays.stream(active).anyMatch(ALLOWED_CLASSPATH_PROFILES::contains);
    }

    private boolean isClasspathPath(String path) {
        return path != null && path.startsWith(CLASSPATH_PREFIX);
    }

    private String rejection(String envVar, String property, String configured) {
        return "Refusing to start auth-service: " + property + "=" + configured
                + " resolves to a bundled classpath resource. "
                + "Set " + envVar + " to a filesystem path supplied by the secret store "
                + "(e.g. file:/var/secrets/jwt/private.pem). "
                + "Active profiles: " + Arrays.toString(environment.getActiveProfiles())
                + ". Allowed for classpath usage: " + ALLOWED_CLASSPATH_PROFILES + ".";
    }
}
