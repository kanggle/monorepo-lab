package com.example.fanplatform.artist.testsupport;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Wraps {@link MockWebServer} that serves the JWKS JSON document at
 * {@code /oauth2/jwks} (the real IAM auth-service route). Bound to all
 * interfaces so containers can reach it via host.docker.internal. Mirrors
 * community-service.
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

    @Override
    public void close() throws IOException {
        server.shutdown();
    }
}
