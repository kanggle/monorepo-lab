package com.example.fanplatform.e2e.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wraps a {@link MockWebServer} that serves the JWKS JSON document produced
 * by {@link JwtTestHelper} at {@code /.well-known/jwks.json}.
 *
 * <p>Binds to all interfaces ({@code 0.0.0.0}) on an OS-chosen ephemeral port
 * so that Testcontainers containers can reach the host JVM via the
 * {@code host.docker.internal} alias (which resolves to the Docker bridge IP,
 * not loopback). MockWebServer's default {@code start()} binds to
 * {@code 127.0.0.1} only, which is unreachable from inside Docker on Linux CI.
 *
 * <p>Adapted from the wms-platform live-pair e2e helper. Kept in the e2e
 * module's testsupport package so we do not import test classes across
 * Gradle module boundaries.
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
        this.server.start(InetAddress.getByName("0.0.0.0"), 0);
    }

    /** URL reachable from the host JVM. NOT usable from inside containers. */
    public String hostJwksUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort() + "/.well-known/jwks.json";
    }

    /**
     * URL reachable from inside a Testcontainers container. Uses
     * {@code host.docker.internal} which the e2e base class enables on each
     * container via {@code withExtraHost("host.docker.internal", "host-gateway")}.
     */
    public String containerJwksUrl() {
        return "http://host.docker.internal:" + server.getPort() + "/.well-known/jwks.json";
    }

    /** OIDC issuer URL reachable from inside containers — used for {@code OIDC_ISSUER_URL}. */
    public String containerIssuerUrl() {
        return "http://host.docker.internal:" + server.getPort();
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
