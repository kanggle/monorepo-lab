package com.example.apigateway.testfixtures;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Reads what a gateway <strong>ships</strong> for its audience check — the value its
 * {@code application.yml} resolves to when no environment variable overrides it — so a gateway
 * suite can pin it (TASK-MONO-696).
 *
 * <p>Why this exists: rejection of an audience mismatch was rolled out in two phases, and each
 * switch of the shipped mode is supposed to be its own reviewed change. A one-word edit to a
 * property default is exactly the kind of change that slips into an unrelated PR. The per-gateway
 * suites use this to fail if the shipped mode is anything but the one they pin, to fail if a
 * deployment file in the project pins the mode to some other value, and to fail if the
 * deployment file that runs the gateway does not pass the mode through as a rollback lever.
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

    /** The environment variable a gateway's {@code audience-mode} placeholder reads. */
    public static final String MODE_ENV = "OIDC_AUDIENCE_MODE";

    /**
     * The only form in which a deployment file may hand the mode to a gateway container: a
     * pass-through of the same variable that defaults to the shipped mode. It exists so the mode
     * can be switched back on a running host by setting one variable and recreating the gateway,
     * without rebuilding anything; with no variable set it changes nothing.
     */
    public static final String MODE_PASS_THROUGH = "${" + MODE_ENV + ":-ENFORCE}";

    /**
     * Every line, in the project's own deployment files, that sets an audience mode to anything
     * other than the pass-through {@link #MODE_PASS_THROUGH}. The population is the project
     * directory's {@code docker-compose*.yml} and {@code .env*} files; comment lines are skipped.
     *
     * <ul>
     *   <li>compose: a line naming the mode is allowed only as {@code OIDC_AUDIENCE_MODE:
     *       ${OIDC_AUDIENCE_MODE:-ENFORCE}} (map form) or {@code - OIDC_AUDIENCE_MODE=${...}}
     *       (list form). Anything else — a fixed {@code SHADOW}, a fixed {@code ENFORCE}, a
     *       pass-through with a different default, the property name {@code audience-mode} — is
     *       a hit.</li>
     *   <li>{@code .env*}: an assignment of the mode to anything but {@code ENFORCE} is a hit — a
     *       file there is read by compose for the whole project, so a {@code SHADOW} in it would
     *       quietly undo the shipped mode through the pass-through above.</li>
     * </ul>
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
    public static List<String> modeOverrides(Path projectDir) {
        List<Path> composeFiles = list(projectDir, "docker-compose", ".yml");
        if (composeFiles.isEmpty()) {
            throw new IllegalStateException(
                    "no docker-compose*.yml under " + projectDir.toAbsolutePath().normalize()
                            + " — the override scan would pass having read nothing");
        }
        List<String> hits = new ArrayList<>();
        for (Path file : composeFiles) {
            scan(file, hits, ShippedAudienceConfig::isAllowedComposeLine);
        }
        for (Path file : list(projectDir, ".env", "")) {
            scan(file, hits, ShippedAudienceConfig::isAllowedEnvLine);
        }
        return hits;
    }

    /**
     * The names of the services in {@code composeFile} whose {@code environment} hands
     * {@link #MODE_ENV} to the container as exactly {@link #MODE_PASS_THROUGH}. Parsed as YAML
     * (merge keys resolved), so a line that sits under the wrong service, or is commented out,
     * does not count.
     *
     * <p>{@link #modeOverrides} alone cannot tell "the pass-through is present" from "the file says
     * nothing about the mode": both have zero hits. This is the half that fails when the line is
     * missing — without it, a rollback lever that was never wired would read as green.
     *
     * @throws IllegalStateException if the file declares no {@code services}
     */
    public static List<String> servicesPassingModeThrough(Path composeFile) {
        Map<String, Object> services = services(composeFile);
        List<String> passing = new ArrayList<>();
        for (Map.Entry<String, Object> service : services.entrySet()) {
            if (service.getValue() instanceof Map<?, ?> body
                    && MODE_PASS_THROUGH.equals(environment(body.get("environment")).get(MODE_ENV))) {
                passing.add(service.getKey());
            }
        }
        return passing.stream().sorted().toList();
    }

    private static boolean isAllowedComposeLine(String trimmed) {
        String line = trimmed.startsWith("- ") ? trimmed.substring(2).trim() : trimmed;
        line = unquote(line);
        return line.equals(MODE_ENV + ": " + MODE_PASS_THROUGH)
                || line.equals(MODE_ENV + ": \"" + MODE_PASS_THROUGH + "\"")
                || line.equals(MODE_ENV + "=" + MODE_PASS_THROUGH);
    }

    private static boolean isAllowedEnvLine(String trimmed) {
        int eq = trimmed.indexOf('=');
        if (eq < 0) {
            return false;
        }
        String key = trimmed.substring(0, eq).trim();
        if (key.startsWith("export ")) {
            key = key.substring("export ".length()).trim();
        }
        String value = unquote(trimmed.substring(eq + 1).trim());
        return key.equals(MODE_ENV) && value.equals("ENFORCE");
    }

    private static String unquote(String s) {
        if (s.length() >= 2
                && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static void scan(Path file, List<String> hits, java.util.function.Predicate<String> allowed) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("#")) {
                continue;
            }
            String upper = trimmed.toUpperCase(Locale.ROOT);
            boolean namesMode = upper.contains("AUDIENCE_MODE") || upper.contains("AUDIENCE-MODE");
            if (namesMode && !allowed.test(trimmed)) {
                hits.add(file + ":" + (i + 1) + ": " + trimmed);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> services(Path composeFile) {
        Object root;
        try (InputStream in = Files.newInputStream(composeFile)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (root instanceof Map<?, ?> map && map.get("services") instanceof Map<?, ?> services) {
            return (Map<String, Object>) services;
        }
        throw new IllegalStateException(composeFile + " declares no services");
    }

    /** Compose accepts {@code environment} as a map or as a list of {@code KEY=VALUE}. */
    private static Map<String, String> environment(Object environment) {
        Map<String, String> result = new LinkedHashMap<>();
        if (environment instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        } else if (environment instanceof List<?> list) {
            for (Object entry : list) {
                String s = String.valueOf(entry);
                int eq = s.indexOf('=');
                result.put(eq < 0 ? s : s.substring(0, eq), eq < 0 ? null : s.substring(eq + 1));
            }
        }
        return result;
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
