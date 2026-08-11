package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the {@link ArtistAccountChecker} bean — the follow-target validation
 * TASK-FAN-BE-045 AC-6 requires and {@code ADR-004} (ACCEPTED — A) shaped.
 *
 * <h2>AC-7 — the rider {@code ADR-004}'s ACCEPT left open, answered explicitly</h2>
 *
 * The ADR asked whether this seam gets an e2e opt-out, and named two acceptable
 * answers: <em>no hatch</em>, or <em>a hatch whose default is the refusing side</em>.
 * <strong>The answer is the second one</strong>, and it changed during
 * implementation because the first premise did not survive measurement.
 *
 * <p>The ticket's own note argued for "no hatch" on the grounds that
 * {@code MembershipCheckerAutoConfig} only needs its switch because
 * membership-service is absent from the v1 live-trio e2e — while artist-service
 * <em>is</em> in that trio. That measured the wrong thing.
 * {@code FanPlatformE2ETestBase} states the actual reason: the trio omits
 * membership-service <b>and iam, "the workload-identity token source"</b>. What
 * the hatch protects is not the callee's presence but the ability to mint a
 * {@code client_credentials} token at all. With no iam in the trio there is no
 * token, so a hatchless check would fail closed on every follow and take
 * {@code ArtistAndPostFlowE2ETest}'s follow step (which asserts 201) red — a
 * fail-closed outage wearing a security control's clothes.
 *
 * <p>So: {@code community.artist-service.enabled}, <b>default validate</b>. The
 * permissive path is {@link UnverifiedArtistAccountChecker}, reachable only by the
 * value {@code false} ({@code havingValue} compares case-insensitively, so
 * {@code FALSE} counts too — pinned as its own test case rather than assumed).
 *
 * <p>🔴 Deliberately the <b>inverse</b> of the {@code AlwaysAllowMembershipChecker}
 * shape. There, the permissive bean is the {@code @ConditionalOnMissingBean}
 * fallback, so anything unanticipated lands on the permissive side and the gate
 * ships silently off ({@code ADR-004} § Decision Drivers 3). Here the <em>real</em>
 * checker is the fallback and the permissive one carries the explicit condition,
 * so every accident lands on validation-ON. {@code ArtistAccountCheckerConfigTest}
 * pins that: flipping it fails a test, not a demo.
 *
 * <p>The durable fix is to put iam in the live trio so no service needs a hatch to
 * be end-to-end tested. That is a separate task (it changes the trio's shape for
 * membership too), named here rather than smuggled in.
 *
 * <p>Tests that need a deterministic verdict override this bean with a
 * {@code @Primary @TestConfiguration} — the same seam the membership gate's tests
 * use — rather than by flipping the production switch.
 *
 * <h2>Why its own token provider</h2>
 *
 * The {@link IamClientCredentialsTokenProvider} is constructed here rather than
 * injected: this call needs a token carrying the {@code artist.read} workload
 * scope, and folding that scope into the shared membership token would couple the
 * two surfaces — an artist-side scope problem would then also close the premium
 * feed gate. Separate providers keep each fail-closed blast radius to its own
 * surface. (The class is a plain POJO with no stereotype annotation, so
 * constructing it directly is the documented usage.)
 */
@Configuration
public class ArtistAccountCheckerConfig {

    /**
     * The explicit opt-out, declared <b>first</b> so the fallback below can back
     * off from it. Bound to {@code havingValue="false"} — only that exact string
     * reaches this bean.
     */
    @Bean
    @ConditionalOnProperty(name = "community.artist-service.enabled", havingValue = "false")
    public ArtistAccountChecker unverifiedArtistAccountChecker() {
        return new UnverifiedArtistAccountChecker();
    }

    /**
     * Production {@link ArtistAccountChecker} — <b>the fallback, on purpose</b>.
     *
     * <p>The safe bean is the {@code @ConditionalOnMissingBean} one and the
     * permissive bean carries the explicit condition. That inversion is the whole
     * design: whatever the configuration does that nobody anticipated — property
     * absent, {@code enabled=TRUE}, {@code =1}, {@code =yes}, a misspelled key —
     * lands here, with validation ON. The {@code AlwaysAllowMembershipChecker}
     * shape has it the other way round, so its accidents land on the permissive
     * side ({@code ADR-004} § Decision Drivers 3).
     *
     * <p>🔴 The first attempt wrote {@code @ConditionalOnProperty(havingValue="true",
     * matchIfMissing=true)} here. {@code matchIfMissing} only applies when the
     * property is <em>absent</em>, so a present-but-other value
     * ({@code enabled=no}) matched <b>neither</b> bean: zero
     * {@code ArtistAccountChecker} beans and community-service refusing to start.
     * {@code ArtistAccountCheckerConfigTest} caught it. The gate never opened —
     * but a deployer typing {@code TRUE} into
     * {@code COMMUNITY_ARTIST_SERVICE_ENABLED} bricked the service, which is not a
     * trade this switch is worth.
     *
     * <p>Bean order is load-bearing and deterministic: {@code @Bean} methods within
     * one {@code @Configuration} class are processed top-to-bottom, so this
     * condition always evaluates after the opt-out above has had its chance —
     * the same reasoning {@code MembershipCheckerAutoConfig} documents, applied to
     * the opposite pair.
     */
    @Bean
    @ConditionalOnMissingBean(ArtistAccountChecker.class)
    public ArtistAccountChecker httpArtistAccountChecker(
            @Value("${iam.internal-client.token-uri:http://iam.local/oauth2/token}") String tokenUri,
            @Value("${iam.internal-client.client-id:community-service-client}") String clientId,
            @Value("${iam.internal-client.client-secret:secret}") String clientSecret,
            @Value("${community.artist-service.scope:artist.read}") String scope,
            @Value("${community.artist-service.base-url:http://artist-service:8080}") String baseUrl,
            @Value("${community.artist-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${community.artist-service.read-timeout-ms:3000}") int readTimeoutMs) {
        IamClientCredentialsTokenProvider tokenProvider = new IamClientCredentialsTokenProvider(
                tokenUri,
                clientId,
                clientSecret,
                scope,
                Duration.ofMillis(connectTimeoutMs),
                Duration.ofMillis(readTimeoutMs));
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenProvider.currentBearer());
                    return execution.execute(request, body);
                })
                .build();
        return new HttpArtistAccountChecker(restClient);
    }
}
