package com.example.fanplatform.gateway.integration;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the gateway's {@code RewritePath} filters correctly strip the
 * {@code /api/v1/} external namespace before forwarding to downstream services
 * (TASK-FAN-BE-005 production fix).
 *
 * <p>4 test cases — one per external route prefix:
 * <ol>
 *   <li>{@code /api/v1/community/posts} → community-service at {@code /api/community/posts}</li>
 *   <li>{@code /api/v1/artists/some-id} → artist-service at {@code /api/artists/some-id}</li>
 *   <li>{@code /api/v1/artist-groups/g1/members} → artist-service at {@code /api/artist-groups/g1/members}</li>
 *   <li>{@code /api/v1/fandoms/f1} → artist-service at {@code /api/fandoms/f1}</li>
 * </ol>
 *
 * <p>Uses the shared {@link GatewayIntegrationBase} infrastructure (Redis + JWKS
 * MockWebServer) and re-uses the same {@code downstream} MockWebServer as the
 * target for all four routes. A {@link Dispatcher} captures the actual path
 * received by the downstream mock so we can assert the rewrite happened.
 */
@Tag("integration")
class GatewayRouteRewriteTest extends GatewayIntegrationBase {

    /**
     * Wires the four routes (community + artists + artist-groups + fandoms) to the
     * shared {@code downstream} MockWebServer. This supplement the base-class wiring
     * (which only registers routes[0] for community) by also registering the three
     * artist-service routes.
     *
     * <p>Spring Cloud Gateway merges these properties with those from
     * {@link GatewayIntegrationBase#wireProperties}; the first entry (index 0) from
     * the base class covers community, so we start at index 1 here for the artist
     * routes.
     */
    @DynamicPropertySource
    static void wireArtistRoutes(DynamicPropertyRegistry registry) {
        // artist-service-artists  (index 1)
        registry.add("spring.cloud.gateway.routes[1].id", () -> "artist-service-artists");
        registry.add("spring.cloud.gateway.routes[1].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[1].predicates[0]",
                () -> "Path=/api/v1/artists/**");
        registry.add("spring.cloud.gateway.routes[1].filters[0]",
                () -> "RewritePath=/api/v1/artists(?<segment>(?:/.*)?), /api/artists${segment}");
        registry.add("spring.cloud.gateway.routes[1].filters[1].name",
                () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.redis-rate-limiter.burstCapacity",
                () -> "120");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.redis-rate-limiter.requestedTokens",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[1].filters[1].args.key-resolver",
                () -> "#{@accountKeyResolver}");

        // artist-service-artist-groups (index 2)
        registry.add("spring.cloud.gateway.routes[2].id", () -> "artist-service-artist-groups");
        registry.add("spring.cloud.gateway.routes[2].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[2].predicates[0]",
                () -> "Path=/api/v1/artist-groups/**");
        registry.add("spring.cloud.gateway.routes[2].filters[0]",
                () -> "RewritePath=/api/v1/artist-groups(?<segment>(?:/.*)?), /api/artist-groups${segment}");
        registry.add("spring.cloud.gateway.routes[2].filters[1].name",
                () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[2].filters[1].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[2].filters[1].args.redis-rate-limiter.burstCapacity",
                () -> "120");
        registry.add("spring.cloud.gateway.routes[2].filters[1].args.redis-rate-limiter.requestedTokens",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[2].filters[1].args.key-resolver",
                () -> "#{@accountKeyResolver}");

        // artist-service-fandoms (index 3)
        registry.add("spring.cloud.gateway.routes[3].id", () -> "artist-service-fandoms");
        registry.add("spring.cloud.gateway.routes[3].uri",
                () -> "http://" + downstream.getHostName() + ":" + downstream.getPort());
        registry.add("spring.cloud.gateway.routes[3].predicates[0]",
                () -> "Path=/api/v1/fandoms/**");
        registry.add("spring.cloud.gateway.routes[3].filters[0]",
                () -> "RewritePath=/api/v1/fandoms(?<segment>(?:/.*)?), /api/fandoms${segment}");
        registry.add("spring.cloud.gateway.routes[3].filters[1].name",
                () -> "RequestRateLimiter");
        registry.add("spring.cloud.gateway.routes[3].filters[1].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[3].filters[1].args.redis-rate-limiter.burstCapacity",
                () -> "120");
        registry.add("spring.cloud.gateway.routes[3].filters[1].args.redis-rate-limiter.requestedTokens",
                () -> "1");
        registry.add("spring.cloud.gateway.routes[3].filters[1].args.key-resolver",
                () -> "#{@accountKeyResolver}");
    }

    @Test
    void communityRouteRewritesV1PrefixToInternalCommunityPath() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"posts\":[]}"));

        String token = jwt.signFanToken("fan-rewrite-1");

        webTestClient.get().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).as("downstream did not receive the request").isNotNull();
        assertThat(received.getPath())
                .as("/api/v1/community/posts must be rewritten to /api/community/posts")
                .isEqualTo("/api/community/posts");
    }

    @Test
    void communityRoutePreservesPathVariablesAndSegments() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"reactions\":[]}"));

        String postId = "0190f3e2-1234-7abc-8def-000000000001";
        String token = jwt.signFanToken("fan-rewrite-2");

        webTestClient.get()
                .uri("/api/v1/community/posts/{postId}/reactions", postId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("nested path-variable segment must be preserved after rewrite")
                .isEqualTo("/api/community/posts/" + postId + "/reactions");
    }

    @Test
    void artistsRouteRewritesV1PrefixToInternalArtistsPath() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{}}"));

        String artistId = "0190f3e2-aaaa-7abc-8def-000000000002";
        String token = jwt.signFanToken("fan-rewrite-3");

        webTestClient.get()
                .uri("/api/v1/artists/{id}", artistId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("/api/v1/artists/{id} must be rewritten to /api/artists/{id}")
                .isEqualTo("/api/artists/" + artistId);
    }

    @Test
    void artistGroupsRouteRewritesV1PrefixToInternalArtistGroupsPath() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"members\":[]}}"));

        String groupId = "0190f3e2-bbbb-7abc-8def-000000000003";
        String token = jwt.signFanToken("fan-rewrite-4");

        webTestClient.get()
                .uri("/api/v1/artist-groups/{id}", groupId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("/api/v1/artist-groups/{id} must be rewritten to /api/artist-groups/{id}")
                .isEqualTo("/api/artist-groups/" + groupId);
    }

    /**
     * Collection-form regression guards (TASK-MONO-044f RC#1b).
     *
     * <p>The previous {@code RewritePath=/api/v1/<base>/(?<segment>.*), ...} regex
     * required a trailing slash, so a {@code POST /api/v1/artists} (no trailing
     * slash, no path variable — the canonical "create on collection" form) was
     * forwarded unchanged to artist-service, where Spring Security's fallback
     * {@code anyRequest().denyAll()} returned 403. The new regex
     * {@code (?<segment>(?:/.*)?)} captures both the empty-segment collection
     * form and the path-variable form. These tests pin that contract.
     */
    @Test
    void communityRouteRewritesCollectionWithoutTrailingSlash() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"postId\":\"x\"}}"));

        String token = jwt.signFanToken("fan-rewrite-collection-1");

        webTestClient.post().uri("/api/v1/community/posts")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("collection POST /api/v1/community/posts must rewrite to /api/community/posts")
                .isEqualTo("/api/community/posts");
    }

    @Test
    void artistsRouteRewritesCollectionWithoutTrailingSlash() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{}}"));

        String token = jwt.signFanToken("fan-rewrite-collection-2");

        webTestClient.post().uri("/api/v1/artists")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("collection POST /api/v1/artists must rewrite to /api/artists")
                .isEqualTo("/api/artists");
    }

    @Test
    void artistGroupsRouteRewritesCollectionWithoutTrailingSlash() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{}}"));

        String token = jwt.signFanToken("fan-rewrite-collection-3");

        webTestClient.post().uri("/api/v1/artist-groups")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isCreated();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("collection POST /api/v1/artist-groups must rewrite to /api/artist-groups")
                .isEqualTo("/api/artist-groups");
    }

    @Test
    void fandomsRouteRewritesCollectionWithoutTrailingSlash() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[]}"));

        String token = jwt.signFanToken("fan-rewrite-collection-4");

        webTestClient.get().uri("/api/v1/fandoms")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("collection GET /api/v1/fandoms must rewrite to /api/fandoms")
                .isEqualTo("/api/fandoms");
    }

    @Test
    void fandomsRouteRewritesV1PrefixToInternalFandomsPath() throws Exception {
        downstream.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"fandomName\":\"Hearts\"}}"));

        String artistId = "0190f3e2-cccc-7abc-8def-000000000004";
        String token = jwt.signFanToken("fan-rewrite-5");

        webTestClient.get()
                .uri("/api/v1/fandoms/{artistId}", artistId)
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest received = downstream.takeRequest(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.getPath())
                .as("/api/v1/fandoms/{artistId} must be rewritten to /api/fandoms/{artistId}")
                .isEqualTo("/api/fandoms/" + artistId);
    }
}
