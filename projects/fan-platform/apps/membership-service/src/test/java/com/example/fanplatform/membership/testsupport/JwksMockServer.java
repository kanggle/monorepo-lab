package com.example.fanplatform.membership.testsupport;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Wraps a {@link MockWebServer} serving the JWKS JSON document at
 * {@code /oauth2/jwks} — the single real IAM auth-service route. Both the
 * end-user decoder ({@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri})
 * and the workload-identity decoder ({@code fanplatform.internal.jwt.jwk-set-uri})
 * now default to this same physical endpoint in production
 * ({@code application.yml}), so this mock collapses to a single served path
 * (previously dual-served two distinct URIs) to match the other 4
 * fan-platform services' {@code JwksMockServer} and actually exercise the
 * real contract. {@code hostJwksUrl()} and
 * {@code hostInternalJwksUrl()} stay as separate accessors — they wire two
 * distinct Spring properties — but now resolve to the same URL, mirroring the
 * production defaults. Bound to all interfaces so containers can reach it.
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
                if (path != null && path.startsWith("/oauth2/jwks")) {
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
        return "http://" + server.getHostName() + ":" + server.getPort() + "/oauth2/jwks";
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
