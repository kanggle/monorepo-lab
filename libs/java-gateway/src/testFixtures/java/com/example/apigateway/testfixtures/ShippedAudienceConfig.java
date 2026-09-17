package com.example.apigateway.testfixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

/**
 * Reads what a gateway <strong>ships</strong> for its audience check — the value its
 * {@code application.yml} resolves to when no environment variable overrides it — so a gateway
 * suite can pin it (TASK-MONO-696).
 *
 * <p>Why this exists: rejection of an audience mismatch is rolled out in two phases, and the
 * switch from the first (shadow) to the second (enforce) is supposed to be its own reviewed
 * change, taken only after the measured mismatch count is zero. A one-word edit to a property
 * default is exactly the kind of change that slips into an unrelated PR. The per-gateway suites
 * use this to fail if the shipped mode is anything but the one they pin, and to fail if a
 * deployment file in the project quietly overrides it.
 *
 * <p>Environment variables are deliberately <em>not</em> consulted: {@code ${VAR:default}}
 * resolves to {@code default}. A placeholder with no default fails — a value that only exists
 * when some environment supplies it is not shipped.
 */
public final class ShippedAudienceConfig {

    private ShippedAudienceConfig() {}

    /**
     * Resolves {@code key} from the classpath {@code application.yml}, ignoring the environment.
     *
     * @throws IllegalStateException if the key is absent
     * @throws IllegalArgumentException if a placeholder in the value has no default
     */
    public static String shippedValue(String key) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();
        String raw = properties == null ? null : properties.getProperty(key);
        if (raw == null) {
            throw new IllegalStateException(
                    "application.yml does not declare '" + key + "' — the gateway ships no value");
        }
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}", ":", null, false);
        return helper.replacePlaceholders(raw, placeholder -> null).trim();
    }

    /**
     * Every line, in the project's own deployment files, that sets an audience mode to
     * {@code ENFORCE}. The population is the project directory's {@code docker-compose*.yml} and
     * {@code .env*} files.
     *
     * <p><strong>Deliberately project-local.</strong> Repository-level files ({@code infra/demo/**},
     * {@code .github/workflows/**}) are not read: a test that reads a path outside its project is
     * invisible both to Gradle's up-to-date check and to the project's PR path filter, so an edit
     * there would leave this assertion cached green on exactly the change it exists to catch
     * (TASK-MONO-683 / TASK-MONO-695). Callers declare the files read here as test-task inputs.
     *
     * @param projectDir the project directory (the one holding {@code docker-compose.yml})
     * @return {@code file:line: text} for each hit; the caller asserts it is empty
     * @throws IllegalStateException if the project directory holds no compose file at all — an
     *                               empty population would make "no override found" vacuous
     */
    public static List<String> enforceOverrides(Path projectDir) {
        List<Path> files = new ArrayList<>();
        files.addAll(list(projectDir, "docker-compose", ".yml"));
        if (files.isEmpty()) {
            throw new IllegalStateException(
                    "no docker-compose*.yml under " + projectDir.toAbsolutePath().normalize()
                            + " — the override scan would pass having read nothing");
        }
        files.addAll(list(projectDir, ".env", ""));

        List<String> hits = new ArrayList<>();
        for (Path file : files) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            for (int i = 0; i < lines.size(); i++) {
                String upper = lines.get(i).toUpperCase(Locale.ROOT);
                boolean namesMode = upper.contains("AUDIENCE_MODE") || upper.contains("AUDIENCE-MODE");
                if (namesMode && upper.contains("ENFORCE")) {
                    hits.add(file + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        }
        return hits;
    }

    private static List<Path> list(Path dir, String prefix, String suffix) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(suffix);
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
