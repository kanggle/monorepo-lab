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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Scenario 3 — visibility tier gating (TASK-FAN-INT-001 § In Scope #3).
 *
 * <p><b>Scope deviation from task spec.</b> The task lists three sub-cases:
 *
 * <ul>
 *   <li>{@code PUBLIC}: any authenticated tenant member -&gt; 200.</li>
 *   <li>{@code MEMBERS_ONLY}: requires {@code MembershipChecker.hasAccess()}
 *       to return {@code false} for at least one fan to verify the 403
 *       {@code MEMBERSHIP_REQUIRED} branch.</li>
 *   <li>{@code PREMIUM}: v1 always passes + emits a WARN log
 *       ("PREMIUM gate bypassed ...").</li>
 * </ul>
 *
 * <p>The {@code MEMBERS_ONLY} sub-case requires swapping out the
 * {@code MembershipChecker} bean. Production registers
 * {@code AlwaysAllowMembershipChecker} via {@code @ConditionalOnMissingBean}
 * (see {@code projects/fan-platform/apps/community-service/src/main/java/.../
 * infrastructure/membership/MembershipCheckerAutoConfig.java}). Since the e2e
 * suite runs the actual built image, no test-time bean override is possible
 * without modifying production sources — and TASK-FAN-INT-001's hard rule
 * forbids that. The PR summary therefore files a follow-up
 * (TASK-FAN-INT-002 candidate) to add a
 * {@code SPRING_PROFILES_ACTIVE=e2e-membership-deny} test profile that
 * swaps the bean, and this test class verifies only PUBLIC + PREMIUM here.
 *
 * <p>The PREMIUM WARN log is verified by tailing the community container
 * stdout via {@code GenericContainer.getLogs()} after the request and
 * grepping for the characteristic message — the production code path
 * (PostAccessGuard.ensureVisibilityAccessible) calls
 * {@code log.warn("PREMIUM gate bypassed ...")} on every PREMIUM access.
 */
class VisibilityTierE2ETest extends FanPlatformE2ETestBase {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("PUBLIC post -> any authenticated tenant member sees 200")
    void publicPostIsReadableByAnyTenantMember() throws Exception {
        // Author publishes
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

        // A different fan reads -> expects 200, body field present.
        String readerToken = jwt.signFanToken(randomAccountId());
        HttpResponse<String> readResp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityPostById(postId)), readerToken)
                .GET().build());
        assertThat(readResp.statusCode())
                .as("PUBLIC post readable by any tenant member")
                .isEqualTo(200);
        JsonNode readJson = objectMapper.readTree(readResp.body());
        assertThat(readJson.get("data").get("body").asText()).isEqualTo(body);
        assertThat(readJson.get("data").get("visibility").asText()).isEqualTo("PUBLIC");
    }

    @Test
    @DisplayName("PREMIUM post -> v1 always-pass + WARN log captured in container stdout")
    void premiumPostBypassesGateAndLogsWarn() throws Exception {
        String authorAccountId = randomAccountId();
        String authorToken = jwt.signFanToken(authorAccountId);
        String body = uniquePostBody("e2e-premium");
        String createBody = """
                {
                  "postType": "FAN_POST",
                  "visibility": "PREMIUM",
                  "title": "PREMIUM visibility test",
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

        // Different reader. v1 always-pass on PREMIUM means a non-author
        // non-operator gets 200 anyway. The behaviour is auditable via the
        // WARN log emitted by PostAccessGuard.ensureVisibilityAccessible
        // (see PostAccessGuard.java § PREMIUM branch).
        String readerToken = jwt.signFanToken(randomAccountId());
        HttpResponse<String> readResp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityPostById(postId)), readerToken)
                .GET().build());
        assertThat(readResp.statusCode())
                .as("PREMIUM is v1 always-pass; non-author readers get 200")
                .isEqualTo(200);

        // Capture container stdout and grep for the canonical WARN line.
        // GenericContainer.getLogs() returns aggregated stdout+stderr since
        // the container started; the substring match below is stable as
        // long as PostAccessGuard.java keeps the same message template.
        String containerLogs = community.getLogs();
        assertThat(containerLogs)
                .as("community-service emits a WARN log line for every PREMIUM bypass")
                .contains("PREMIUM gate bypassed");
        assertThat(containerLogs)
                .as("WARN line names the affected post id so audit pipelines can correlate")
                .contains(postId);
    }

    @Test
    @DisplayName("MEMBERS_ONLY post -> v1 stub allows access (follow-up: bean-swap test profile)")
    void membersOnlyPostUnderV1StubAllowsAccess() throws Exception {
        // v1 production registers AlwaysAllowMembershipChecker via
        // @ConditionalOnMissingBean (see MembershipCheckerAutoConfig). Until
        // a test-only deny profile lands (filed as a follow-up; see class
        // javadoc), MEMBERS_ONLY behaves identically to PUBLIC in e2e.
        // Asserting the 200 path here at least guards against a regression
        // where MEMBERS_ONLY breaks the happy path entirely.
        String authorAccountId = randomAccountId();
        String authorToken = jwt.signFanToken(authorAccountId);
        String body = uniquePostBody("e2e-members-only");
        String createBody = """
                {
                  "postType": "FAN_POST",
                  "visibility": "MEMBERS_ONLY",
                  "title": "MEMBERS_ONLY visibility test",
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

        String readerToken = jwt.signFanToken(randomAccountId());
        HttpResponse<String> readResp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityPostById(postId)), readerToken)
                .GET().build());
        // Under v1 stub: AlwaysAllowMembershipChecker -> hasAccess()=true ->
        // PostAccessGuard.ensureVisibilityAccessible passes. Asserting 200
        // here documents the v1 behaviour explicitly so the deny path can
        // be added by a future task without ambiguity.
        assertThat(readResp.statusCode())
                .as("v1 AlwaysAllowMembershipChecker grants access — 200 expected")
                .isEqualTo(200);

        // The stub also emits a WARN log per call. Cross-check it surfaces.
        String containerLogs = community.getLogs();
        assertThat(containerLogs)
                .as("AlwaysAllowMembershipChecker emits a WARN line on every bypass call")
                .contains("Membership gate bypassed (v1 stub)");
    }
}
