package com.example.erp.gateway.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wraps a {@link MockWebServer} that serves the JWKS JSON document produced by
 * {@link JwtTestHelper} at {@code /oauth2/jwks} (GAP V0013's standard SAS endpoint — the erp
 * gateway's {@code jwk-set-uri} default is {@code .../oauth2/jwks}, not the legacy
 * {@code /.well-known/jwks.json}). The gateway's resource-server fetches this to verify signatures.
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
        // Bind to all interfaces so a Testcontainers-hosted process could reach it if needed.
        this.server.start(InetAddress.getByName("0.0.0.0"), 0);
    }

    public String hostJwksUrl() {
        // The gateway under test runs in THIS JVM, so it connects over loopback. The server is
        // bound to 0.0.0.0 (all interfaces, so 127.0.0.1 is included); use the explicit loopback
        // literal rather than getHostName(), which for a 0.0.0.0-bound server can return
        // "0.0.0.0" — an address a client cannot connect to on Windows, yielding a JWKS-fetch
        // failure that surfaces as a 500 on every token-bearing request.
        return "http://127.0.0.1:" + server.getPort() + "/oauth2/jwks";
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
