package com.example.fanplatform.e2e.scenario;

import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.authedGet;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.authedJson;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityPostById;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.pathCommunityPosts;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.randomAccountId;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.sendString;
import static com.example.fanplatform.e2e.testsupport.E2ETestFixtures.uniquePostBody;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.fanplatform.e2e.testsupport.FanPlatformE2ETestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Scenario 3 — visibility tier gating, against the <b>real</b> membership gate
 * (TASK-FAN-INT-006; originally TASK-FAN-INT-001 § In Scope #3, realigned once by
 * TASK-FAN-INT-002).
 *
 * <p><b>What changed.</b> This class used to assert the behaviour of an inert stub.
 * The stack had no membership-service, so community ran with
 * {@code COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false} and
 * {@code AlwaysAllowMembershipChecker} answered every gated read with "yes" plus a
 * WARN line — which is what these tests asserted. membership-service is now in the
 * stack, the escape hatch is deleted, and every assertion below exercises
 * {@code HttpMembershipChecker} → {@code /internal/membership/access} for real.
 *
 * <h2>Why the ACTIVE membership is created through the product path</h2>
 *
 * <p>TASK-FAN-INT-006 AC-0 weighed three ways to get an ACTIVE row: a payment stub,
 * an e2e-only Flyway seed, or a direct INSERT. The product path won on a measured
 * fact rather than on preference — {@code MockPaymentGatewayAdapter} is
 * {@code @Profile("!portone")}, so it is already the {@code PaymentGatewayPort} in
 * any stack that does not opt into PortOne. There was no payment plane to stand up.
 * At equal cost the product path proves strictly more: the row these tests rely on
 * is written by the real {@code SubscribeUseCase} — real amount computation, real
 * idempotency key, real outbox — not by a fixture that only resembles one.
 *
 * <p>🔵 The stub PG is more permissive than the real one. That permissiveness is
 * confined to the payment AUTHORIZATION step, which is not what this class proves;
 * what it proves is the membership gate, and the membership row is genuine.
 *
 * <h2>Why there are three verdicts, not two</h2>
 *
 * <p>"Member gets 200, non-member gets 403" is satisfiable by a gate that is broken
 * in one direction. If {@code MEMBERSHIP_SERVICE_BASE_URL} were wrong, every read
 * would fail-closed to 403 — and a suite that only checked the deny case would go
 * green on a completely dead gate. So the deny cases are always paired with an
 * allow case in the same run, and one deny is a <b>tier</b> deny: a MEMBERS_ONLY
 * subscriber reading a PREMIUM post. That one cannot be produced by broken wiring,
 * because the same subscriber is granted a MEMBERS_ONLY read moments earlier. It is
 * the case that distinguishes "the gate answers" from "the gate is reachable".
 */
@Tag("full")
class VisibilityTierE2ETest extends FanPlatformE2ETestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** External gateway path; rewritten to {@code /api/fan/memberships} downstream. */
    private static final String PATH_MEMBERSHIPS = "/api/v1/memberships";

    /**
     * Subscribes {@code accountId} at {@code tier} through the gateway and returns
     * the created membership id. Asserts 201 — a silent non-2xx here would make
     * every later assertion a measurement of the wrong thing.
     */
    private String subscribe(String accountId, String tier) throws Exception {
        String token = jwt.signFanToken(accountId);
        String body = """
                {
                  "tier": "%s",
                  "planMonths": 1,
                  "paymentId": "e2e-%s"
                }
                """.formatted(tier, UUID.randomUUID());

        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(PATH_MEMBERSHIPS), token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());

        assertThat(resp.statusCode())
                .as("subscribe %s for %s must succeed — body=%s", tier, accountId, resp.body())
                .isEqualTo(201);
        JsonNode json = objectMapper.readTree(resp.body()).get("data");
        assertThat(json.get("status").asText())
                .as("the row the gate will read must actually be ACTIVE")
                .isEqualTo("ACTIVE");
        assertThat(json.get("tier").asText()).isEqualTo(tier);
        return json.get("membershipId").asText();
    }

    /** Publishes a post at {@code visibility} and returns its id. */
    private String publish(String visibility, String marker) throws Exception {
        String authorToken = jwt.signFanToken(randomAccountId());
        String body = uniquePostBody(marker);
        String createBody = """
                {
                  "postType": "FAN_POST",
                  "visibility": "%s",
                  "title": "%s visibility test",
                  "body": "%s"
                }
                """.formatted(visibility, visibility, body);

        HttpResponse<String> resp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathCommunityPosts()), authorToken)
                .POST(HttpRequest.BodyPublishers.ofString(createBody))
                .build());
        assertThat(resp.statusCode()).isEqualTo(201);
        return objectMapper.readTree(resp.body()).get("data").get("postId").asText();
    }

    private HttpResponse<String> readAs(String postId, String accountId) throws Exception {
        return sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityPostById(postId)),
                jwt.signFanToken(accountId))
                .GET().build());
    }

    @Test
    @DisplayName("PUBLIC post -> any authenticated tenant member sees 200 (gate not consulted)")
    void publicPostIsReadableByAnyTenantMember() throws Exception {
        String authorAccountId = randomAccountId();
        String authorToken = jwt.signFanToken(authorAccountId);
        String body = uniquePostBody("e2e-public");
        String createBody = """
                {
                  "postType": "FAN_POST",
                  "visibility": "PUBLIC",
                  "title": "PUBLIC visibility test",
                  "body": "%s"
                }
                """.formatted(body);

        HttpResponse<String> createResp = sendString(http, authedJson(
                gatewayBaseUri().resolve(pathCommunityPosts()), authorToken)
                .POST(HttpRequest.BodyPublishers.ofString(createBody))
                .build());
        assertThat(createResp.statusCode()).isEqualTo(201);
        String postId = objectMapper.readTree(createResp.body())
                .get("data").get("postId").asText();

        HttpResponse<String> readResp = readAs(postId, randomAccountId());
        assertThat(readResp.statusCode())
                .as("PUBLIC post readable by any tenant member, with no membership at all")
                .isEqualTo(200);
        JsonNode readJson = objectMapper.readTree(readResp.body());
        assertThat(readJson.get("data").get("body").asText()).isEqualTo(body);
        assertThat(readJson.get("data").get("visibility").asText()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("MEMBERS_ONLY -> subscriber 200 AND non-subscriber 403 MEMBERSHIP_REQUIRED, same run")
    void membersOnlyGrantsSubscribersAndDeniesEveryoneElse() throws Exception {
        String postId = publish("MEMBERS_ONLY", "e2e-members-only");

        // Deny half — no membership row exists for this account at all.
        String strangerId = randomAccountId();
        HttpResponse<String> denied = readAs(postId, strangerId);
        assertThat(denied.statusCode())
                .as("no membership -> the real gate denies (this is the assertion the "
                        + "AlwaysAllow stub could never satisfy) — body=%s", denied.body())
                .isEqualTo(403);
        assertThat(objectMapper.readTree(denied.body()).get("code").asText())
                .isEqualTo("MEMBERSHIP_REQUIRED");

        // Allow half — in the SAME run, so a fail-closed gate (wrong base URL,
        // unreachable service, token failure) cannot make the deny above pass.
        String memberId = randomAccountId();
        subscribe(memberId, "MEMBERS_ONLY");
        HttpResponse<String> allowed = readAs(postId, memberId);
        assertThat(allowed.statusCode())
                .as("ACTIVE MEMBERS_ONLY membership -> 200 — body=%s", allowed.body())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("PREMIUM -> PREMIUM subscriber 200, MEMBERS_ONLY subscriber 403 (tier deny), "
            + "non-subscriber 403")
    void premiumDistinguishesTierNotJustPresence() throws Exception {
        String postId = publish("PREMIUM", "e2e-premium");

        // 1. No membership at all.
        HttpResponse<String> stranger = readAs(postId, randomAccountId());
        assertThat(stranger.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(stranger.body()).get("code").asText())
                .isEqualTo("MEMBERSHIP_REQUIRED");

        // 2. 🔴 The load-bearing case. This account HAS an ACTIVE membership and is
        // still denied, because MEMBERS_ONLY does not grant PREMIUM (AccessPolicy:
        // PREMIUM ⊇ MEMBERS_ONLY, not the reverse). Broken wiring cannot produce
        // this verdict selectively — the same account is granted its own tier below.
        String lesserMemberId = randomAccountId();
        subscribe(lesserMemberId, "MEMBERS_ONLY");
        HttpResponse<String> insufficient = readAs(postId, lesserMemberId);
        assertThat(insufficient.statusCode())
                .as("MEMBERS_ONLY membership must NOT open PREMIUM — body=%s", insufficient.body())
                .isEqualTo(403);
        assertThat(objectMapper.readTree(insufficient.body()).get("code").asText())
                .isEqualTo("MEMBERSHIP_REQUIRED");

        // 2b. The control for case 2: the very same account reads a MEMBERS_ONLY post
        // successfully. Without this, case 2 is indistinguishable from "the gate
        // denies everything".
        String membersOnlyPostId = publish("MEMBERS_ONLY", "e2e-tier-control");
        assertThat(readAs(membersOnlyPostId, lesserMemberId).statusCode())
                .as("the tier-denied account is granted its OWN tier — proves the 403 above "
                        + "is a tier verdict, not an unreachable membership-service")
                .isEqualTo(200);

        // 3. Allow half.
        String premiumMemberId = randomAccountId();
        subscribe(premiumMemberId, "PREMIUM");
        HttpResponse<String> allowed = readAs(postId, premiumMemberId);
        assertThat(allowed.statusCode())
                .as("ACTIVE PREMIUM membership -> 200 — body=%s", allowed.body())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("the inert stub's bypass WARN never appears — the hatch is gone, not just unused")
    void noStubBypassWarnIsEverEmitted() {
        // TASK-FAN-INT-006. The stub logged this line on every gated read. Its absence
        // after a run that made several gated reads is a direct, executable statement
        // that HttpMembershipChecker — not the fallback — answered them.
        //
        // 🔵 This asserts an ABSENCE, so it is only meaningful because the tests above
        // ran gated reads in this same container. Kept in this class for that reason
        // rather than in a standalone one.
        assertThat(community.getLogs())
                .as("AlwaysAllowMembershipChecker is deleted; its bypass WARN must be unreachable")
                .doesNotContain("Membership gate bypassed");
    }
}
