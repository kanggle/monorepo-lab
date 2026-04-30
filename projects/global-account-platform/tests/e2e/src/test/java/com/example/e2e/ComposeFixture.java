package com.example.e2e;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * JUnit 5 extension that ensures docker-compose.e2e.yml services are running
 * before the test suite starts. Keeps everything up for the whole JVM to hit
 * the 5-minute wall clock budget declared in TASK-BE-041c §7.
 *
 * <p>Detects whether services are already healthy (manual {@code docker compose up})
 * and only starts compose if needed. Uses the Docker CLI directly instead of
 * Testcontainers ComposeContainer to avoid Windows npipe compatibility issues.
 *
 * <p>Per-class isolation is achieved in {@link E2EBase#resetDataBetweenClasses()}
 * through TRUNCATE statements rather than compose recreation.
 */
public final class ComposeFixture implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Object LOCK = new Object();
    private static boolean STARTED = false;
    private static boolean SELF_MANAGED = false;

    // Ports mapped in docker-compose.e2e.yml.
    public static final int ADMIN_PORT = 18085;
    public static final int AUTH_PORT = 18081;
    public static final int ACCOUNT_PORT = 18082;
    public static final int SECURITY_PORT = 18084;
    public static final int MYSQL_PORT = 3306;
    public static final int KAFKA_PORT = 9092;

    public static final String HOST = "127.0.0.1";

    public static final String ADMIN_BASE_URL = "http://" + HOST + ":" + ADMIN_PORT;
    public static final String ACCOUNT_BASE_URL = "http://" + HOST + ":" + ACCOUNT_PORT;
    public static final String SECURITY_BASE_URL = "http://" + HOST + ":" + SECURITY_PORT;

    private static final String[] HEALTH_URLS = {
            "http://" + HOST + ":" + AUTH_PORT + "/actuator/health",
            "http://" + HOST + ":" + ACCOUNT_PORT + "/actuator/health",
            "http://" + HOST + ":" + SECURITY_PORT + "/actuator/health",
            "http://" + HOST + ":" + ADMIN_PORT + "/actuator/health"
    };

    private static final Duration HEALTH_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofSeconds(5);

    @Override
    public void beforeAll(ExtensionContext context) {
        start();
        context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL)
                .put("compose-fixture-singleton", this);
    }

    public static void start() {
        synchronized (LOCK) {
            if (STARTED) return;

            if (allServicesHealthy()) {
                System.out.println("[ComposeFixture] All services already healthy — skipping compose up");
                STARTED = true;
                return;
            }

            System.out.println("[ComposeFixture] Services not healthy — starting docker compose");
            File composeFile = locateComposeFile();
            startCompose(composeFile);
            SELF_MANAGED = true;

            waitForHealthy();
            STARTED = true;
            Runtime.getRuntime().addShutdownHook(new Thread(ComposeFixture::stopQuietly, "compose-shutdown"));
        }
    }

    private static void startCompose(File composeFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose", "-f", composeFile.getAbsolutePath(),
                    "-p", "gap-e2e", "up", "-d", "--build"
            );
            pb.inheritIO();
            pb.directory(composeFile.getParentFile());
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("docker compose up failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("docker compose up interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to run docker compose", e);
        }
    }

    private static void waitForHealthy() {
        Instant deadline = Instant.now().plus(HEALTH_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (allServicesHealthy()) {
                System.out.println("[ComposeFixture] All services healthy");
                return;
            }
            try {
                Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for services", e);
            }
        }
        throw new IllegalStateException("Services did not become healthy within " + HEALTH_TIMEOUT);
    }

    private static boolean allServicesHealthy() {
        for (String url : HEALTH_URLS) {
            if (!isHealthy(url)) return false;
        }
        return true;
    }

    private static boolean isHealthy(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void stopQuietly() {
        if (!SELF_MANAGED) return;
        try {
            File composeFile = locateComposeFile();
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "compose", "-f", composeFile.getAbsolutePath(),
                    "-p", "gap-e2e", "down", "-v"
            );
            pb.inheritIO();
            pb.directory(composeFile.getParentFile());
            pb.start().waitFor();
        } catch (Throwable ignored) {
            // best effort
        }
    }

    private static File locateComposeFile() {
        File dir = new File(".").getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++) {
            File candidate = new File(dir, "docker-compose.e2e.yml");
            if (candidate.isFile()) return candidate;
            dir = dir.getParentFile();
        }
        throw new IllegalStateException("docker-compose.e2e.yml not found via directory walk");
    }

    @Override
    public void close() {
        // Suite-wide teardown. No-op — JVM shutdown hook handles stop()
    }

    /** External Kafka bootstrap for host-side producers/consumers (DLQ scenario). */
    public static final String KAFKA_BOOTSTRAP_HOST = "127.0.0.1:19092";

    /** Host-mapped MySQL port (docker-compose.e2e.yml). */
    public static final int MYSQL_HOST_PORT = 13306;

    public static String mysqlJdbcUrl(String db, String user, String password) {
        return "jdbc:mysql://" + HOST + ":" + MYSQL_HOST_PORT + "/" + db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&user="
                + user + "&password=" + password;
    }
}
