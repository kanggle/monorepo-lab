package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.fanplatform.community.infrastructure.artist.HttpArtistAccountChecker;
import com.example.fanplatform.community.infrastructure.jpa.FollowJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Follow-gate integration test — TASK-FAN-BE-045 AC-6 (ADR-004 ACCEPTED — A),
 * asserted through the HTTP API.
 *
 * <h2>Why this test uses the REAL checker</h2>
 *
 * Unlike {@link MembershipGateIntegrationTest}, which swaps the port for a stub,
 * this class deliberately keeps the production {@link HttpArtistAccountChecker}
 * bean and stubs the far side of the wire instead (a MockWebServer standing in for
 * artist-service's {@code /internal/artists/exists} plus iam's token endpoint,
 * wired in via {@code @DynamicPropertySource}). AC-6's verdict is <em>"an
 * unreachable artist-service must not open follow"</em>, and a test that replaced
 * the adapter with a stub could not fail when the adapter is switched off — it
 * would pin the stub's behaviour, not the product's. The bean type is therefore
 * asserted explicitly in every case here.
 *
 * <p>The three cases are ordered because the fail-closed case shuts the stub
 * server down and never brings it back.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FollowArtistGateIntegrationTest extends CommunityServiceIntegrationBase {

    /** Accounts the stubbed artist-service confirms. Everything else is denied. */
    private static final Set<String> CONFIRMED_ACCOUNTS = ConcurrentHashMap.newKeySet();

    private static final MockWebServer ARTIST_STUB = new MockWebServer();

    static {
        ARTIST_STUB.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                if (path.startsWith("/oauth2/token")) {
                    return json("{\"access_token\":\"stub-workload-token\","
                            + "\"token_type\":\"Bearer\",\"expires_in\":600}");
                }
                if (path.startsWith("/internal/artists/exists")) {
                    String accountId = queryParam(path, "accountId");
                    boolean exists = accountId != null && CONFIRMED_ACCOUNTS.contains(accountId);
                    return json("{\"exists\":" + exists + "}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        try {
            ARTIST_STUB.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }

    private static String queryParam(String path, String name) {
        int q = path.indexOf('?');
        if (q < 0) return null;
        for (String pair : path.substring(q + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    @DynamicPropertySource
    static void wireArtistStub(DynamicPropertyRegistry registry) {
        registry.add("community.artist-service.base-url", () -> ARTIST_STUB.url("/").toString());
        registry.add("iam.internal-client.token-uri", () -> ARTIST_STUB.url("/oauth2/token").toString());
        // Short timeouts: the fail-closed case must reach its verdict quickly.
        // ArtistAccountCheckerConfig hands these same two values to its token
        // provider as well, so both legs of the call are bounded.
        registry.add("community.artist-service.connect-timeout-ms", () -> "1000");
        registry.add("community.artist-service.read-timeout-ms", () -> "1000");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    FollowJpaRepository followJpaRepository;

    @Autowired
    ArtistAccountChecker artistAccountChecker;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    private HttpHeaders authHeaders(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * A realistic account id: a bare UUID, 36 characters.
     *
     * <p>🔴 Not a readable prefix + UUID. Account ids are {@code VARCHAR(36)}
     * throughout this schema and {@code FollowArtistRequest} enforces
     * {@code @Size(max = 36)}, so {@code "artist-acct-" + UUID} (48) is an input
     * the product can never produce: it is rejected as {@code VALIDATION_ERROR}
     * at the controller boundary, <b>before</b> the follow-target check runs.
     * Every case in this class would then assert 422 for the wrong reason — the
     * deny case would still "pass" its status assertion while proving nothing
     * about the gate. CI caught this; the prefixed ids read perfectly well.
     */
    private static String accountId() {
        return UUID.randomUUID().toString();
    }

    private ResponseEntity<String> follow(String fanId, String artistAccountId) {
        return rest.exchange(
                url("/api/community/follows"),
                HttpMethod.POST,
                new HttpEntity<>("{\"artistAccountId\":\"" + artistAccountId + "\"}",
                        authHeaders(jwt.signFanToken(fanId))),
                String.class);
    }

    /**
     * Guards the whole class. Production carries an explicit opt-out
     * ({@code community.artist-service.enabled=false} → {@code
     * UnverifiedArtistAccountChecker}, AC-7). If that switch — or an env var, or a
     * future test-profile default — ever flipped for this suite, every case below
     * would still be green while proving nothing: an accept-everything checker
     * answers 201 for the confirmed target too. So the bean type is asserted, not
     * assumed.
     */
    private void assertRealCheckerIsWired() {
        assertThat(artistAccountChecker)
                .as("this suite is worthless against a stubbed checker — AC-6 is about the real adapter")
                .isInstanceOf(HttpArtistAccountChecker.class);
    }

    @Test
    @Order(1)
    @DisplayName("artist-service 가 확인해 주는 대상 → POST /api/community/follows 201 + follows 행 생성")
    void followTargetConfirmedByArtistService_created() {
        assertRealCheckerIsWired();
        String fanId = accountId();
        String artistAccountId = accountId();
        CONFIRMED_ACCOUNTS.add(artistAccountId);

        ResponseEntity<String> res = follow(fanId, artistAccountId);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(followJpaRepository.findByFanAccountIdAndArtistAccountIdAndTenantId(
                fanId, artistAccountId, "fan-platform")).isPresent();
    }

    @Test
    @Order(2)
    @DisplayName("artist-service 가 거절하는 대상 → 422 UNKNOWN_ARTIST_ACCOUNT + follows 행 0건")
    void followTargetDeniedByArtistService_rejectedAndNotPersisted() throws Exception {
        assertRealCheckerIsWired();
        String fanId = accountId();
        String bogusTarget = accountId(); // never added to CONFIRMED_ACCOUNTS

        ResponseEntity<String> res = follow(fanId, bogusTarget);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode err = objectMapper.readTree(res.getBody());
        assertThat(err.path("code").asText()).isEqualTo("UNKNOWN_ARTIST_ACCOUNT");

        // The status code alone would pass for an implementation that writes the
        // row and then fails; assert the absence of the row itself.
        assertThat(followJpaRepository.findByFanAccountIdAndArtistAccountIdAndTenantId(
                fanId, bogusTarget, "fan-platform")).isEmpty();
        assertThat(followJpaRepository.count())
                .as("a rejected follow must leave the follows table untouched")
                .isZero();
    }

    /**
     * 🔴 The load-bearing assertion of AC-6. The target used here <em>is</em> in the
     * stub's confirmed set — the only thing changed relative to
     * {@link #followTargetConfirmedByArtistService_created()} is that
     * artist-service cannot be reached. Follow must be REFUSED, not admitted.
     *
     * <p>If this ever goes green while the checker is disabled, the check is
     * fail-open and equivalent to no validation at all — hence
     * {@link #assertRealCheckerIsWired()} runs first and the far side is a real
     * socket that is really closed, not a mocked exception.
     *
     * <p>Runs last: the stub server does not come back up.
     */
    @Test
    @Order(3)
    @DisplayName("🔴 FAIL-CLOSED: artist-service 도달 불가 → 팔로우가 열리지 않는다 (422, 행 0건)")
    void artistServiceUnreachable_followIsRefusedNotAdmitted() throws Exception {
        assertRealCheckerIsWired();
        String fanId = accountId();
        String otherwiseValidTarget = accountId();
        CONFIRMED_ACCOUNTS.add(otherwiseValidTarget); // would be a 201 if the service were up

        ARTIST_STUB.shutdown(); // nothing listening → connection refused

        ResponseEntity<String> res = follow(fanId, otherwiseValidTarget);

        assertThat(res.getStatusCode())
                .as("an unreachable validator must refuse, not admit an unverified target")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode err = objectMapper.readTree(res.getBody());
        assertThat(err.path("code").asText()).isEqualTo("UNKNOWN_ARTIST_ACCOUNT");

        assertThat(followJpaRepository.findByFanAccountIdAndArtistAccountIdAndTenantId(
                fanId, otherwiseValidTarget, "fan-platform")).isEmpty();
        assertThat(followJpaRepository.count())
                .as("fail-closed means no row — an outage must not become a write")
                .isZero();
    }
}
