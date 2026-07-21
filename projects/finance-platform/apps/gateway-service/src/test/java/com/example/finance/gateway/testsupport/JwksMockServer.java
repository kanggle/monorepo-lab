package com.example.finance.gateway.testsupport;

import java.io.IOException;
import java.net.InetAddress;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Wraps a {@link MockWebServer} that serves the JWKS JSON document produced by
 * {@link JwtTestHelper} at {@code /oauth2/jwks} — the endpoint finance's
 * {@code application.yml} defaults its {@code jwk-set-uri} to (GAP's SAS endpoint,
 * not the legacy {@code /.well-known/jwks.json}).
 *
 * <p>The gateway's reactive resource server ({@code GatewayJwtDecoders.nimbus})
 * fetches this document and verifies inbound JWT signatures against the public key,
 * so tokens signed by {@link JwtTestHelper} validate against the same keypair.
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
        // Bind to all interfaces so a Testcontainers-hosted process could reach this
        // server via host.docker.internal if ever needed.
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
