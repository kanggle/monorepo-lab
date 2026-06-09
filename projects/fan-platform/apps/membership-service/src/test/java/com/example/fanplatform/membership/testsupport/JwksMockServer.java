package com.example.fanplatform.membership.testsupport;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Wraps a {@link MockWebServer} serving the JWKS JSON document. Serves the same
 * JWKS at both {@code /.well-known/jwks.json} (end-user decoder) and
 * {@code /oauth2/jwks} (workload-identity decoder) since the test keypair signs
 * both token kinds. Bound to all interfaces so containers can reach it.
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
                if (path != null
                        && (path.startsWith("/.well-known/jwks.json") || path.startsWith("/oauth2/jwks"))) {
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

    public String hostJwksUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort() + "/.well-known/jwks.json";
    }

    public String hostInternalJwksUrl() {
        return "http://" + server.getHostName() + ":" + server.getPort() + "/oauth2/jwks";
    }

    public String hostIssuer() {
        // The token issuer claim is JwtTestHelper.SAS_ISSUER; the internal
        // decoder pins this value (the JWKS URL is separate).
        return JwtTestHelper.SAS_ISSUER;
    }

    @Override
    public void close() throws IOException {
        server.shutdown();
    }
}
