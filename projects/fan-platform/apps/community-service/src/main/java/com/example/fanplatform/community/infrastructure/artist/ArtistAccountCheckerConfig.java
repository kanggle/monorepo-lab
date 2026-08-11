package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.springframework.beans.factory.annotation.Value;
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
 * <h2>AC-7 — there is no e2e escape hatch, and that is the answer, not an omission</h2>
 *
 * {@code ADR-004}'s ACCEPT left one rider open: whether this seam gets an
 * opt-out switch like {@code community.membership-service.enabled}. It does not,
 * for a measured reason rather than a preference.
 *
 * <p>{@code MembershipCheckerAutoConfig} has that switch because the v1 live-trio
 * e2e stack (TASK-FAN-INT-001) brings up gateway + community + <b>artist</b> and
 * does <b>not</b> bring up membership-service or iam — so without an opt-out the
 * membership call had nothing to talk to. <strong>That reason does not transfer:
 * artist-service is already in the trio.</strong> The condition that justified the
 * hatch is false here, so adding one would only create a way to ship this check
 * switched off.
 *
 * <p>🔴 Deliberately NOT copied: {@code AlwaysAllowMembershipChecker}'s shape. A
 * fallback that returns {@code true} unconditionally makes a service that has the
 * validation wired and disabled look exactly like one that passes — the failure
 * mode {@code ADR-004} § Decision Drivers 3 names. If a future deployment truly
 * needs an opt-out, the default must be <em>deny</em>, and it must be pinned by a
 * test that fails when the default flips.
 *
 * <p>Tests that need a deterministic verdict override this bean with a
 * {@code @Primary @TestConfiguration} — the same seam the membership gate's tests
 * use — rather than a production switch.
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
     * Production {@link ArtistAccountChecker}. No {@code @ConditionalOnProperty}
     * — see the class Javadoc (AC-7): this bean has no off switch by design.
     */
    @Bean
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
