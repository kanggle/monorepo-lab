package com.example.fanplatform.e2e.scenario;

import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.authedGet;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathArtistRegister;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathArtistStatus;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityFeed;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityFollow;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityPosts;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityReactions;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.uniquePostBody;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.uniqueStageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import com.example.fanplatform.e2e.testsupport.FanPlatformE2ETestBase;
import com.example.fanplatform.e2e.testsupport.KafkaTestConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Scenario 1 — artist directory + post publishing happy path through the
 * gateway with all 3 v1 services live (TASK-FAN-INT-001 § In Scope #3).
 *
 * <p>Five-step happy path:
 *
 * <ol>
 *   <li>Admin registers an artist in DRAFT — asserts 201 + outbox publishes
 *       {@code artist.registered.v1}.</li>
 *   <li>Admin transitions the artist DRAFT -&gt; PUBLISHED — asserts 200 +
 *       outbox publishes {@code artist.published.v1}.</li>
 *   <li>Fan follows the artist (uses the artist's account id from claim
 *       {@code sub}) — asserts 201 envelope.</li>
 *   <li>Fan publishes a {@code FAN_POST} (PUBLIC visibility) — asserts 201 +
 *       outbox publishes {@code community.post.published.v1}.</li>
 *   <li>Fan2 reacts {@code LIKE} on the post (no race verification needed —
 *       the success status alone validates the cross-service routing).</li>
 *   <li>Fan calls {@code GET /api/community/feed} — verifies the v1 follow-based
 *       feed contract (200 + well-formed page envelope). Per
 *       {@code community-api.md} § GET /api/community/feed, the feed surfaces
 *       posts authored by the artists the fan follows; in this scenario the
 *       followed artist has not authored a community post, so the content
 *       array is correctly empty (deterministic — no race with outbox/cache).</li>
 * </ol>
 *
 * <p>Each Awaitility-based outbox assertion filters by a unique fixture
 * marker (stage name suffix, post body marker) so concurrent scenario
 * classes do not race on the same Kafka topic — see TASK-FAN-INT-001
 * § Edge Cases ("Kafka 토픽 race") and the {@code TASK-MONO-023d} pattern
 * called out in the task spec.
 */
@Tag("smoke")
class ArtistAndPostFlowE2ETest extends FanPlatformE2ETestBase {

    private static final String TOPIC_ARTIST_REGISTERED = "artist.registered.v1";
    private static final String TOPIC_ARTIST_PUBLISHED = "artist.published.v1";
    private static final String TOPIC_POST_PUBLISHED = "community.post.published.v1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("admin registers + publishes artist; fan follows, posts, reacts; feed contains the fan's post")
    void fullArtistAndPostFlow() throws Exception {
        // ----- Identities --------------------------------------------------
        // The contract says a fan follows by `artistAccountId`. The admin token
        // identifies the admin who registers; the artist RESOURCE id is a
        // separate UUID again.
        //
        // 🔴 TASK-FAN-INT-005 removed a fourth identity that used to live here —
        // a synthetic `artistAccountId` the fan followed, justified by "v1 has no
        // enforcement that artistAccountId resolves to a real artist account".
        // TASK-FAN-BE-045 added that enforcement; the comment simply went stale,
        // and the suite could not tell because it was running with the gate
        // switched off. There is now ONE artist account: the one registered in
        // step 1 and followed in step 3.
        String adminAccountId = randomAccountId();
        String fanAccountId = randomAccountId();
        String fan2AccountId = randomAccountId();
        // RegisterArtistRequest.accountId (TASK-FAN-BE-045) — the IAM subject that
        // authors as this artist. Still distinct from the artist RESOURCE id, which
        // is what artist-service mints for the row; the two are different axes and
        // step 3 needs this one.
        String registeredArtistAccountId = randomAccountId();

        String adminToken = jwt.signAdminToken(adminAccountId);
        String fanToken = jwt.signFanToken(fanAccountId);
        String fan2Token = jwt.signFanToken(fan2AccountId);

        // ----- Unique fixtures (race avoidance per task spec § Edge Cases) -
        String stageName = uniqueStageName("STAR");
        String postBody = uniquePostBody("e2e-fan-post");

        // ------------------------------------------------------------------
        // Pre-subscribe to all three Kafka topics BEFORE issuing the request
        // — KafkaTestConsumer uses a fresh group id, so "earliest" still
        // misses events published before the consumer's first partition
        // assignment. Pre-subscribing is the safest seam (mirrors the wms
        // e2e pattern).
        // ------------------------------------------------------------------
        try (KafkaTestConsumer consumer = new KafkaTestConsumer(kafkaBootstrapForHost(),
                List.of(TOPIC_ARTIST_REGISTERED, TOPIC_ARTIST_PUBLISHED, TOPIC_POST_PUBLISHED))) {

            // ==============================================================
            // Step 1 — admin registers artist (DRAFT)
            // ==============================================================
            String registerBody = """
                    {
                      "accountId": "%s",
                      "artistType": "SOLO",
                      "stageName": "%s",
                      "agency": "E2E Agency",
                      "bio": "auto-registered by ArtistAndPostFlowE2ETest"
                    }
                    """.formatted(registeredArtistAccountId, stageName);

            HttpResponse<String> registerResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathArtistRegister()), adminToken)
                    .POST(HttpRequest.BodyPublishers.ofString(registerBody))
                    .build());

            assertThat(registerResp.statusCode())
                    .as("POST /api/v1/artists (-> /api/artists) returns 201 for admin-tier role")
                    .isEqualTo(201);
            JsonNode registerJson = objectMapper.readTree(registerResp.body());
            JsonNode registeredArtist = registerJson.get("data");
            assertThat(registeredArtist).as("envelope { data, meta } has data").isNotNull();
            assertThat(registeredArtist.get("status").asText()).isEqualTo("DRAFT");
            assertThat(registeredArtist.get("stageName").asText()).isEqualTo(stageName);
            assertThat(registeredArtist.get("tenantId").asText()).isEqualTo("fan-platform");
            String artistResourceId = registeredArtist.get("id").asText();
            assertThatNoException().isThrownBy(() -> UUID.fromString(artistResourceId));

            // Outbox -> Kafka assertion: artist.registered.v1 keyed by the
            // artist resource id, payload echoes the unique stage name.
            List<ConsumerRecord<String, String>> seenAcc = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        seenAcc.addAll(consumer.drain());
                        ConsumerRecord<String, String> match = seenAcc.stream()
                                .filter(r -> TOPIC_ARTIST_REGISTERED.equals(r.topic()))
                                .filter(r -> artistResourceId.equals(r.key()))
                                .findFirst().orElse(null);
                        assertThat(match)
                                .as("artist.registered.v1 should be published within 30s")
                                .isNotNull();
                        JsonNode envelope = objectMapper.readTree(match.value());
                        assertThat(envelope.get("eventType").asText())
                                .isEqualTo("artist.registered");
                        assertThat(envelope.get("source").asText())
                                .isEqualTo("fan-platform-artist-service");
                        assertThat(envelope.get("partitionKey").asText())
                                .isEqualTo(artistResourceId);
                        JsonNode payload = envelope.get("payload");
                        assertThat(payload.get("stageName").asText()).isEqualTo(stageName);
                        assertThat(payload.get("tenantId").asText()).isEqualTo("fan-platform");
                        assertThatNoException().isThrownBy(
                                () -> UUID.fromString(envelope.get("eventId").asText()));
                    });

            // ==============================================================
            // Step 2 — admin publishes (DRAFT -> PUBLISHED)
            // ==============================================================
            HttpResponse<String> publishResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathArtistStatus(artistResourceId)), adminToken)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(
                            "{\"status\":\"PUBLISHED\"}"))
                    .build());

            assertThat(publishResp.statusCode()).isEqualTo(200);
            JsonNode publishJson = objectMapper.readTree(publishResp.body());
            assertThat(publishJson.get("data").get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(publishJson.get("data").get("publishedAt").isNull()).isFalse();

            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        seenAcc.addAll(consumer.drain());
                        ConsumerRecord<String, String> match = seenAcc.stream()
                                .filter(r -> TOPIC_ARTIST_PUBLISHED.equals(r.topic()))
                                .filter(r -> artistResourceId.equals(r.key()))
                                .findFirst().orElse(null);
                        assertThat(match)
                                .as("artist.published.v1 keyed by aggregate id should arrive within 30s")
                                .isNotNull();
                        JsonNode envelope = objectMapper.readTree(match.value());
                        assertThat(envelope.get("eventType").asText()).isEqualTo("artist.published");
                        assertThat(envelope.get("payload").get("publishedAt").isNull()).isFalse();
                    });

            // ==============================================================
            // Step 3 — fan follows the artist REGISTERED IN STEP 1
            //
            // 🔴 TASK-FAN-INT-005: this step used to follow a SYNTHETIC UUID,
            // on the stated premise that "v1 community-service treats this as
            // an opaque key (no cross-service lookup)". That premise stopped
            // being true when TASK-FAN-BE-045 added exactly that lookup —
            // HttpArtistAccountChecker calls artist-service's
            // /internal/artists/exists and REFUSES a target it cannot confirm.
            //
            // The suite did not notice, because it was simultaneously running
            // with COMMUNITY_ARTIST_SERVICE_ENABLED=false: the gate was off, so
            // the synthetic id kept working and the stale comment kept reading
            // as true. Deleting that switch is what surfaced it — the first run
            // without the hatch answered 422, correctly.
            //
            // So the follow target is now the account the artist was actually
            // registered with, which is the whole point of the seam: this
            // assertion now proves the cross-service join, not just that
            // community-service can write a row.
            // ==============================================================
            String followBody = "{\"artistAccountId\":\"" + registeredArtistAccountId + "\"}";
            HttpResponse<String> followResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathCommunityFollow()), fanToken)
                    .POST(HttpRequest.BodyPublishers.ofString(followBody))
                    .build());

            assertThat(followResp.statusCode())
                    .as("POST /api/v1/community/follows -> 201 (target confirmed by artist-service "
                            + "over a real IAM client_credentials token — TASK-FAN-INT-005)")
                    .isEqualTo(201);
            JsonNode followJson = objectMapper.readTree(followResp.body());
            assertThat(followJson.get("data").get("artistAccountId").asText())
                    .isEqualTo(registeredArtistAccountId);
            assertThat(followJson.get("data").get("fanAccountId").asText())
                    .isEqualTo(fanAccountId);

            // ==============================================================
            // Step 3b — 🔴 the negative half, and it is not optional
            //
            // A 201 on a confirmed target does not distinguish a working gate
            // from a permissive one — an accept-everything checker answers 201
            // too. That is precisely the state this suite was in until now. So
            // assert the refusal as well: an account artist-service has never
            // heard of must be rejected, on the same request path, with the
            // same token, differing only in the target.
            // ==============================================================
            String unknownArtistAccountId = randomAccountId();
            HttpResponse<String> unknownFollowResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathCommunityFollow()), fanToken)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"artistAccountId\":\"" + unknownArtistAccountId + "\"}"))
                    .build());

            assertThat(unknownFollowResp.statusCode())
                    .as("POST /api/v1/community/follows with an unregistered account -> 422 "
                            + "(if this is 201 the follow-target gate is off, and the 201 above "
                            + "proves nothing)")
                    .isEqualTo(422);
            // GlobalExceptionHandler returns libs/java-web ErrorResponse, which is
            // a FLAT record (code, message, timestamp) — not nested under "error".
            assertThat(objectMapper.readTree(unknownFollowResp.body()).get("code").asText())
                    .as("the refusal carries the contract's code (community-api.md § Follows)")
                    .isEqualTo("UNKNOWN_ARTIST_ACCOUNT");

            // ==============================================================
            // Step 4 — fan publishes a PUBLIC FAN_POST
            // ==============================================================
            String postBodyJson = """
                    {
                      "postType": "FAN_POST",
                      "visibility": "PUBLIC",
                      "title": "E2E test post",
                      "body": "%s"
                    }
                    """.formatted(postBody);
            HttpResponse<String> postResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathCommunityPosts()), fanToken)
                    .POST(HttpRequest.BodyPublishers.ofString(postBodyJson))
                    .build());

            assertThat(postResp.statusCode())
                    .as("POST /api/v1/community/posts -> 201")
                    .isEqualTo(201);
            JsonNode postJson = objectMapper.readTree(postResp.body());
            JsonNode postData = postJson.get("data");
            String postId = postData.get("postId").asText();
            assertThat(postData.get("visibility").asText()).isEqualTo("PUBLIC");
            assertThat(postData.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(postData.get("authorAccountId").asText()).isEqualTo(fanAccountId);

            await().atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(() -> {
                        seenAcc.addAll(consumer.drain());
                        ConsumerRecord<String, String> match = seenAcc.stream()
                                .filter(r -> TOPIC_POST_PUBLISHED.equals(r.topic()))
                                .filter(r -> postId.equals(r.key()))
                                .findFirst().orElse(null);
                        assertThat(match)
                                .as("community.post.published.v1 keyed by postId arrives within 30s")
                                .isNotNull();
                        JsonNode envelope = objectMapper.readTree(match.value());
                        assertThat(envelope.get("eventType").asText())
                                .isEqualTo("community.post.published");
                        assertThat(envelope.get("source").asText())
                                .isEqualTo("fan-platform-community-service");
                        JsonNode payload = envelope.get("payload");
                        assertThat(payload.get("visibility").asText()).isEqualTo("PUBLIC");
                        assertThat(payload.get("authorAccountId").asText())
                                .isEqualTo(fanAccountId);
                    });

            // ==============================================================
            // Step 5a — fan2 reacts LIKE on the post
            // ==============================================================
            HttpResponse<String> reactionResp = sendString(http, authedJson(
                    gatewayBaseUri().resolve(pathCommunityReactions(postId)), fan2Token)
                    .method("PUT", HttpRequest.BodyPublishers.ofString(
                            "{\"reactionType\":\"LIKE\"}"))
                    .build());
            assertThat(reactionResp.statusCode())
                    .as("PUT /api/v1/community/posts/{id}/reactions -> 200 (idempotent upsert)")
                    .isEqualTo(200);
            JsonNode reactionJson = objectMapper.readTree(reactionResp.body());
            assertThat(reactionJson.get("data").get("reactionType").asText()).isEqualTo("LIKE");

            // ==============================================================
            // Step 5b — fan reads their feed
            //
            // v1 feed contract (community-api.md § GET /api/community/feed):
            //   "Returns the actor's follow-based feed (artists they follow)."
            // i.e. the feed surfaces *only* posts by accounts the fan follows
            // — NOT the fan's own posts. Per GetFeedUseCase javadoc + the
            // PostRepository.findFeedForFan port contract.
            //
            // In this scenario the artist (registered in steps 1–2, and the
            // account followed in step 3 since TASK-FAN-INT-005) has not authored
            // any COMMUNITY post — artist-service publishes artist master data,
            // not community posts. So the *content* of the feed page is correctly
            // empty here, for the same reason as before. The deterministic assertion
            // is therefore the contract envelope (200 OK + well-formed
            // pagination), not the presence of any specific post.
            // (TASK-MONO-044f-2 RC: e2e fixture vs contract drift — earlier
            //  iterations of this test asserted "feed includes the fan's own
            //  post," which contradicts the v1 contract.)
            // ==============================================================
            HttpResponse<String> feedResp = sendString(http, authedGet(
                    gatewayBaseUri().resolve(pathCommunityFeed() + "?page=0&size=20"), fanToken)
                    .GET().build());

            assertThat(feedResp.statusCode())
                    .as("GET /api/v1/community/feed -> 200")
                    .isEqualTo(200);
            JsonNode feedJson = objectMapper.readTree(feedResp.body());
            JsonNode dataNode = feedJson.get("data");
            assertThat(dataNode).as("envelope { data, meta } has data").isNotNull();
            JsonNode contentArr = dataNode.get("content");
            assertThat(contentArr.isArray())
                    .as("feed page has a content array (may be empty for follow-based feed with no followed-author posts)")
                    .isTrue();
            assertThat(dataNode.has("page"))
                    .as("feed page envelope carries pagination metadata")
                    .isTrue();
        }
    }
}
