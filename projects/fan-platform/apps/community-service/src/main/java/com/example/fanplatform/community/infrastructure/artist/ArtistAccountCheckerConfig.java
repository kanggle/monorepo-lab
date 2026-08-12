package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * <h2>The e2e escape hatch is GONE (TASK-FAN-INT-005)</h2>
 *
 * {@code ADR-004}'s ACCEPT left a rider: does this seam get an e2e opt-out? It named
 * two acceptable answers — <em>no hatch</em>, or <em>a hatch whose default is the
 * refusing side</em> — and TASK-FAN-BE-045 chose the second, for a reason it had to
 * correct mid-implementation. The reason was never that artist-service was missing
 * (it was in the trio all along); it was that the trio had <b>no IAM</b>, so no
 * {@code client_credentials} token could be minted at all and a hatchless check
 * would fail closed on every follow — a fail-closed outage wearing a security
 * control's clothes.
 *
 * <p><strong>That condition no longer holds.</strong> {@code FanPlatformE2ETestBase}
 * now boots iam's auth-service and MySQL alongside the fan services, community mints
 * a real token, and the suite exercises this gate for real. The permissive bean and
 * the {@code community.artist-service.enabled} property are therefore deleted: the
 * answer to ADR-004's rider is now <b>no hatch</b> — the option the ADR preferred
 * in the first place, unavailable until the stack could support it.
 *
 * <p>🔴 The {@code community.artist-service.*} prefix survives below for
 * {@code scope} / {@code base-url} / timeouts, and that is deliberate — those are
 * ordinary configuration. What is gone is the <em>enabled</em> key. No value of
 * anything selects a permissive checker, and
 * {@code ArtistAccountCheckerConfigTest} asserts that twice over: behaviourally
 * (every value of the old key still yields the real checker) and structurally (this
 * class declares exactly one {@code ArtistAccountChecker} {@code @Bean} method, so
 * an opt-out reintroduced behind a <em>different</em> key fails too).
 *
 * <p>🔵 Contrast with the sibling {@code MembershipCheckerAutoConfig}, whose switch
 * is still live: there the permissive bean is the {@code @ConditionalOnMissingBean}
 * fallback, so anything unanticipated lands on the permissive side and the gate can
 * ship silently off ({@code ADR-004} § Decision Drivers 3). Removing that one needs
 * membership-service <em>and</em> an ACTIVE membership row, which the product creates
 * only through the PortOne-backed subscribe flow — a materially bigger job, split out
 * as TASK-FAN-INT-006 rather than assumed to be the same size as this one.
 *
 * <p>Tests that need a deterministic verdict override this bean with a
 * {@code @Primary @TestConfiguration} — the same seam the membership gate's tests
 * use. That was always the supported way; it is now the only way.
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
     * The one and only {@link ArtistAccountChecker} — TASK-FAN-INT-005 deleted the
     * opt-out bean that used to be declared above it.
     *
     * <p>{@code @ConditionalOnMissingBean} is <b>kept</b>, and not because a
     * production alternative might appear. It is the seam integration tests use to
     * substitute a deterministic verdict ({@code @Primary @TestConfiguration}), and
     * with the property gone it is now the only way to do that. It also keeps the
     * safe bean on the fallback side, so any future contribution that fails to
     * register still lands on validation-ON rather than on nothing.
     *
     * <p>🔴 History worth keeping, because the shape looks arbitrary otherwise: the
     * first attempt wrote {@code @ConditionalOnProperty(havingValue="true",
     * matchIfMissing=true)} here. {@code matchIfMissing} applies only when the
     * property is <em>absent</em>, so a present-but-other value ({@code enabled=no})
     * matched <b>neither</b> bean — zero {@code ArtistAccountChecker} beans and
     * community-service refusing to start. The gate never opened, but a deployer
     * typing {@code TRUE} would have bricked the service.
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
