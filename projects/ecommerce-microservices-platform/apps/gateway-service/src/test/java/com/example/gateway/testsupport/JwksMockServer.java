package com.example.gateway.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wraps a {@link MockWebServer} that serves the JWKS JSON document produced
 * by {@link JwtTestHelper} at {@code /.well-known/jwks.json}. The server binds
 * to an OS-chosen ephemeral port; the URL is handed to the Spring Boot context
 * via {@code DynamicPropertySource} so Spring Security's oauth2 resource-server
 * can validate signatures against the same key.
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
        // Bind to all interfaces so the server is reachable from the test JVM.
        this.server.start(InetAddress.getByName("0.0.0.0"), 0);
    }

    /**
     * URL reachable from the host JVM (used with {@code DynamicPropertySource}
     * for {@code @SpringBootTest} contexts running inside the same JVM).
     */
    public String hostJwksUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort()
                + "/.well-known/jwks.json";
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
