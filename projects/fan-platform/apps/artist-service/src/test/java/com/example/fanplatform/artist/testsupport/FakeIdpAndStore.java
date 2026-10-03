package com.example.fanplatform.artist.testsupport;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * TASK-MONO-759 — the two HTTP edges the store seller lookup crosses, as one real HTTP server:
 *
 * <ul>
 *   <li>{@code POST /oauth2/token} — the IdP. Answers the {@code client_credentials} grant and
 *       the RFC 8693 exchange; the exchange is granted only for {@code audience=ecommerce}
 *       (the {@code WorkloadTenantCatalog} entry) and only when the presented subject token is
 *       the one it issued — anything else is {@code 400 invalid_grant}, like the issuer.</li>
 *   <li>{@code GET /internal/sellers/{id}} — the ecommerce gateway → product-service. Admits only
 *       the EXCHANGED token (the plain cc token is 403, as the gateway's tenant check would make
 *       it); answers from {@link #sellers}, else 404 {@code SELLER_NOT_FOUND}.</li>
 * </ul>
 *
 * <p>Both edges can be taken down independently ({@link #idpUp} / {@link #storeUp}), which is
 * what AC-2's «스토어/IdP 를 내린 상태» needs. «Down» is a dropped connection, not a 5xx — the
 * shape a dead host actually has.
 *
 * <p>This is a stand-in for real services, not for the adapter: the adapter under test speaks
 * real HTTP to it, so the path, headers, form fields and status mapping are all exercised.
 */
public final class FakeIdpAndStore implements AutoCloseable {

    public static final String CC_TOKEN = "cc-token-fan-platform";
    public static final String STORE_TOKEN = "exchanged-token-ecommerce";

    private final MockWebServer server = new MockWebServer();

    /** sellerId → raw 200 JSON body. Absent → 404 SELLER_NOT_FOUND. */
    public final Map<String, String> sellers = new ConcurrentHashMap<>();
    /** sellerId → [status, body] overrides for odd answers (500, a codeless 404, …). */
    public final Map<String, Object[]> overrides = new ConcurrentHashMap<>();
    public final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    public volatile boolean idpUp = true;
    public volatile boolean storeUp = true;
    /** Delay before the store answers — longer than the adapter's read timeout = a timeout. */
    public volatile long storeDelayMs = 0;

    public FakeIdpAndStore() throws IOException {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requests.add(request);
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/oauth2/token")) {
                    return idpUp ? token(request) : dead();
                }
                if (path.startsWith("/internal/sellers/")) {
                    if (!storeUp) {
                        return dead();
                    }
                    MockResponse r = seller(request, path.substring("/internal/sellers/".length()));
                    return storeDelayMs > 0
                            ? r.setHeadersDelay(storeDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                            : r;
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
    }

    public String baseUrl() {
        String u = server.url("/").toString();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    public String tokenUri() {
        return baseUrl() + "/oauth2/token";
    }

    public void active(String sellerId, String status) {
        sellers.put(sellerId, "{\"sellerId\":\"" + sellerId + "\",\"status\":\"" + status + "\"}");
    }

    public long storeCalls() {
        return requests.stream().filter(r -> r.getPath() != null
                && r.getPath().startsWith("/internal/sellers/")).count();
    }

    private static MockResponse dead() {
        return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START);
    }

    private MockResponse token(RecordedRequest request) {
        Map<String, String> form = form(request.getBody().clone().readUtf8()); // clone: tests re-read the body
        String grant = form.getOrDefault("grant_type", "");
        if ("client_credentials".equals(grant)) {
            return json(200, "{\"access_token\":\"" + CC_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":1800}");
        }
        if ("urn:ietf:params:oauth:grant-type:token-exchange".equals(grant)
                && CC_TOKEN.equals(form.get("subject_token"))
                && "ecommerce".equals(form.get("audience"))
                && "store.seller.read".equals(form.get("scope"))) {
            return json(200, "{\"access_token\":\"" + STORE_TOKEN + "\",\"token_type\":\"Bearer\",\"expires_in\":600}");
        }
        return json(400, "{\"error\":\"invalid_grant\"}");
    }

    private MockResponse seller(RecordedRequest request, String sellerId) {
        if (!("Bearer " + STORE_TOKEN).equals(request.getHeader("Authorization"))) {
            return json(403, "{\"code\":\"FORBIDDEN\",\"message\":\"workload credential required\"}");
        }
        Object[] o = overrides.get(sellerId);
        if (o != null) {
            return json((Integer) o[0], (String) o[1]);
        }
        String body = sellers.get(sellerId);
        if (body == null) {
            return json(404, "{\"code\":\"SELLER_NOT_FOUND\",\"message\":\"Seller not found: " + sellerId + "\"}");
        }
        return json(200, body);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json").setBody(body);
    }

    private static Map<String, String> form(String body) {
        Map<String, String> out = new ConcurrentHashMap<>();
        for (String pair : body.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        server.shutdown();
    }
}
