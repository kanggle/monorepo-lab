package com.example.fanplatform.e2e.testsupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Common request builders and fixture data generators for the fan-platform
 * v1 e2e suite (TASK-FAN-INT-001).
 *
 * <p>The unique-suffix helpers ({@link #uniqueStageName(String)},
 * {@link #uniquePostBody(String)}) follow the
 * {@code TASK-MONO-023d} race-avoidance pattern called out in the task spec
 * § Edge Cases: each scenario differentiates its fixtures so a Kafka topic
 * shared across scenarios cannot accidentally satisfy another scenario's
 * Awaitility assertion.
 */
public final class E2ETestFixtures {

    public static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);

    private E2ETestFixtures() {}

    // ------------------------------------------------------------------
    // Path helpers — gateway-prefixed (`/api/v1/...`) paths used by tests
    // ------------------------------------------------------------------

    /** Gateway path that fronts {@code POST /api/artists} on artist-service. */
    public static String pathArtistRegister() {
        return "/api/v1/artist/artists";
    }

    /** Gateway path that fronts {@code PATCH /api/artists/{id}/status} on artist-service. */
    public static String pathArtistStatus(String artistId) {
        return "/api/v1/artist/artists/" + artistId + "/status";
    }

    /** Gateway path that fronts {@code GET /api/artists/{id}} on artist-service. */
    public static String pathArtistById(String artistId) {
        return "/api/v1/artist/artists/" + artistId;
    }

    /** Gateway path that fronts {@code POST /api/community/follows} on community-service. */
    public static String pathCommunityFollow() {
        return "/api/v1/community/follows";
    }

    /** Gateway path that fronts {@code POST /api/community/posts} on community-service. */
    public static String pathCommunityPosts() {
        return "/api/v1/community/posts";
    }

    /** Gateway path that fronts {@code GET /api/community/posts/{id}} on community-service. */
    public static String pathCommunityPostById(String postId) {
        return "/api/v1/community/posts/" + postId;
    }

    /** Gateway path that fronts {@code PUT /api/community/posts/{postId}/reactions} on community-service. */
    public static String pathCommunityReactions(String postId) {
        return "/api/v1/community/posts/" + postId + "/reactions";
    }

    /** Gateway path that fronts {@code GET /api/community/feed} on community-service. */
    public static String pathCommunityFeed() {
        return "/api/v1/community/feed";
    }

    // ------------------------------------------------------------------
    // Fixture data generators — unique per call to avoid cross-scenario races
    // ------------------------------------------------------------------

    public static String uniqueStageName(String prefix) {
        // 8-char hex suffix gives ample collision-resistance for a single CI run.
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String uniquePostBody(String prefix) {
        return prefix + " body marker " + UUID.randomUUID();
    }

    public static String randomAccountId() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    public static HttpRequest.Builder authedJson(URI uri, String bearerToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Forwarded-For", "10.0.0." + (1 + (int) (Math.random() * 250)));
    }

    public static HttpRequest.Builder authedGet(URI uri, String bearerToken) {
        return HttpRequest.newBuilder(uri)
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .header("Accept", "application/json")
                .header("X-Forwarded-For", "10.0.0." + (1 + (int) (Math.random() * 250)));
    }

    public static HttpResponse<String> sendString(HttpClient http, HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
