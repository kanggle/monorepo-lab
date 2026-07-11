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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Scenario 3 — visibility tier gating (TASK-FAN-INT-001 § In Scope #3,
 * realigned by TASK-FAN-INT-002 after TASK-FAN-BE-010).
 *
 * <p>The live-trio stack is gateway+community+artist only — membership-service
 * and iam (the workload-identity token source) are out of scope. After
 * TASK-FAN-BE-010 made {@code HttpMembershipChecker} the production default, the
 * community container would fail-closed on every MEMBERS_ONLY/PREMIUM read in
 * this stack. The container therefore opts out via the documented escape hatch
 * {@code COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false} (see
 * {@code MembershipCheckerAutoConfig}), so community falls back to
 * {@code AlwaysAllowMembershipChecker} — the v1 stub. The real HTTP gate (incl.
 * the 403 {@code MEMBERSHIP_REQUIRED} deny branch) is covered deterministically
 * by {@code MembershipGateIntegrationTest} (MockWebServer, PR-gated) and
 * end-to-end by federation-hardening-e2e; this class covers the cross-service
 * wiring under the stub:
 *
 * <ul>
 *   <li>{@code PUBLIC}: any authenticated tenant member -&gt; 200 (no checker
 *       call).</li>
 *   <li>{@code MEMBERS_ONLY}: stub {@code hasAccess()=true} -&gt; 200 + WARN
 *       log.</li>
 *   <li>{@code PREMIUM}: stub {@code hasAccess()=true} -&gt; 200 + WARN log.</li>
 * </ul>
 *
 * <p>The bypass WARN is verified by tailing the community container stdout via
 * {@code GenericContainer.getLogs()} after the request and grepping for the
 * characteristic message — {@code AlwaysAllowMembershipChecker.hasAccess} calls
 * {@code log.warn("Membership gate bypassed (inert fallback stub selected ...):
 * account=.. tier=.. tenant=..")} on every gated read, so the fail-open is loud
 * in logs/observability.
 */
@Tag("full")
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

    /**
     * Asserts the behaviour of the <b>inert fallback stub</b>, not the production gate.
     * This live trio deliberately sets {@code COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false}
     * (FAN-INT-002) so {@code AlwaysAllowMembershipChecker} is selected instead of
     * {@code HttpMembershipChecker}; the always-pass + WARN is that stub's escape-hatch
     * behaviour. <b>Production hard fail-closes on PREMIUM</b> (FAN-BE-010) — covered by
     * {@code MembershipGateIntegrationTest} and federation-hardening-e2e.
     *
     * <p>The name used to read "v1 always-pass", which is how a reader grepping for
     * always-pass kept finding a test that appeared to assert an open PREMIUM gate in
     * production (TASK-MONO-354).
     */
    @Test
    @DisplayName("PREMIUM post -> inert stub (membership-service disabled) always-passes + WARN log "
            + "captured in container stdout — NOT the production gate, which fail-closes")
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

        // Different reader. Under the v1 stub (COMMUNITY_MEMBERSHIP_SERVICE_ENABLED
        // =false) AlwaysAllowMembershipChecker.hasAccess returns true, so the
        // PostAccessGuard PREMIUM branch passes and a non-author non-operator
        // gets 200. The bypass is auditable via the stub's WARN log.
        String readerToken = jwt.signFanToken(randomAccountId());
        HttpResponse<String> readResp = sendString(http, authedGet(
                gatewayBaseUri().resolve(pathCommunityPostById(postId)), readerToken)
                .GET().build());
        assertThat(readResp.statusCode())
                .as("PREMIUM under v1 stub: non-author readers get 200")
                .isEqualTo(200);

        // Capture container stdout and grep for the canonical WARN line.
        // GenericContainer.getLogs() returns aggregated stdout+stderr since
        // the container started; the substring match below is stable as long
        // as AlwaysAllowMembershipChecker keeps the same message template.
        String containerLogs = community.getLogs();
        assertThat(containerLogs)
                .as("community-service emits a WARN log line for every stub bypass")
                .contains("Membership gate bypassed (inert fallback stub selected");
        assertThat(containerLogs)
                .as("WARN line names the required tier so audit pipelines can correlate")
                .contains("tier=PREMIUM");
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
                .contains("Membership gate bypassed (inert fallback stub selected");
        assertThat(containerLogs)
                .as("WARN line names the required tier for the MEMBERS_ONLY read")
                .contains("tier=MEMBERS_ONLY");
    }
}
