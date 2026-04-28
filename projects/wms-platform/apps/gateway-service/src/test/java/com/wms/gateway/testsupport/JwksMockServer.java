package com.wms.gateway.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wraps an {@link MockWebServer} that serves the JWKS JSON document produced
 * by {@link JwtTestHelper} at {@code /.well-known/jwks.json}. The server binds
 * to an OS-chosen ephemeral port; the URL is handed to the gateway container
 * via the {@code JWT_JWKS_URI} env var.
 */
public final class JwksMockServer implements AutoCloseable {

    private final MockWebServer server;
    private final String jwks;

    public JwksMockServer(JwtTestHelper jwt) throws IOException {
        this.jwks = jwt.jwksJson();
        this.server = new MockWebServer();
        this.server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.startsWith("/.well-known/jwks.json")) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(jwks);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        // Bind to all interfaces so containers can reach this server via
        // host.docker.internal (which resolves to the Docker bridge IP, not
        // loopback). MockWebServer.start() default binds to 127.0.0.1 only,
        // which is unreachable from inside Docker containers on Linux CI.
        this.server.start(InetAddress.getByName("0.0.0.0"), 0);
    }

    /**
     * URL reachable from the host. Useful for wiring into a Spring Boot
     * context running inside the JVM — NOT usable from a container since
     * {@code localhost} inside a container points at the container itself.
     */
    public String hostJwksUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort() + "/.well-known/jwks.json";
    }

    /**
     * URL reachable from inside a Testcontainers container. Uses the Docker
     * host gateway alias ({@code host.docker.internal}) which the e2e base
     * class enables via {@code withExtraHost}.
     */
    public String containerJwksUrl() {
        return "http://host.docker.internal:" + server.getPort() + "/.well-known/jwks.json";
    }

    public int port() {
        return server.getPort();
    }

    public String jwksJson() {
        return jwks;
    }

    @Override
    public void close() throws IOException {
        server.shutdown();
    }
}
