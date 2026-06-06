package com.example.scmplatform.gateway.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wraps a {@link MockWebServer} that serves the JWKS JSON document produced
 * by {@link JwtTestHelper} at {@code /oauth2/jwks}.
 *
 * <p>The path matches GAP V0013's standard endpoint (per Edge Case E2 — V0013
 * SQL seeded {@code jwks_uri = http://iam.local/oauth2/jwks}, not the legacy
 * {@code /.well-known/jwks.json}).
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
                if (path != null && (path.startsWith("/oauth2/jwks")
                        || path.startsWith("/.well-known/jwks.json"))) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .setHeader("Content-Type", "application/json")
                            .setBody(jwks);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        // Bind to all interfaces so containers (Testcontainers) can reach this
        // server via host.docker.internal.
        this.server.start(InetAddress.getByName("0.0.0.0"), 0);
    }

    public String hostJwksUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort() + "/oauth2/jwks";
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
