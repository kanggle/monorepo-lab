package com.example.scmplatform.logistics.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.scmplatform.logistics.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.scmplatform.logistics.adapter.inbound.web.controller.DispatchController;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.usecase.RetryDispatchUseCase;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.FirewallRejections;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-801 — a path the servlet firewall rejects up front, through the real
 * {@code FilterChainProxy} built from this service's {@link SecurityConfig} (shape A:
 * {@code denyAll()} tail + JSON 401 entry point).
 *
 * <p>Before the {@code RequestRejectedHandler} bean, Spring Security's default handler called
 * {@code sendError(400)}; on a real container the {@code /error} dispatch then met the deny-all
 * chain anonymous and the client got 401 «Authentication required» (TASK-MONO-801 AC-0, measured
 * on Tomcat). MockMvc performs no error dispatch, so here the red is a 400 with an EMPTY body —
 * the envelope assertions are what fail.
 *
 * <p>The URI is passed as a {@link URI} so MockMvc keeps the raw path (a template string would
 * be re-encoded, and {@code //x} would parse as an authority).
 */
@WebMvcTest(DispatchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "scmplatform.test.slice=firewall-rejection")
@DisplayName("logistics — TASK-MONO-801 up-front firewall rejection (real chain)")
class FirewallRejectionSliceTest {

    private static final String ISSUER = "http://iam.local";
    private static final UUID DISPATCH_ID = UUID.fromString("0192dddd-0000-0000-0000-000000000801");
    private static final String GET_PATH = "/api/logistics/dispatches/" + DISPATCH_ID;
    private static final String RETRY_PATH = "/api/logistics/dispatches/" + DISPATCH_ID + ":retry";
    private static final KeyPair KEYS = generateRsaKeyPair();

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DispatchPersistencePort persistencePort;

    @MockitoBean
    RetryDispatchUseCase retryDispatchUseCase;

    private final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @TestConfiguration
    static class RealJwtChainConfig {
        @Bean
        JwtDecoder jwtDecoder() {
            ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
            ReflectionTestUtils.setField(config, "requiredTenantId", "scm");
            ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
            ReflectionTestUtils.setField(config, "jwkSetUri", ISSUER + "/oauth2/jwks");
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(config.jwtTokenValidator());
            return decoder;
        }
    }

    @BeforeEach
    void captureLogs() {
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(logs);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/x;y",
            "/api/logistics/dispatches;jsessionid=abc",
            "/api/logistics//dispatches",
            "/api/logistics/%25",
            "/api/%2E%2E/logistics"})
    @DisplayName("🔴 AC-1 rejected path, no token → 400 BAD_REQUEST envelope, path not echoed, one WARN, no ERROR")
    void rejectedPath_anonymous_answers400(String rawPath) throws Exception {
        MvcResult result = mockMvc.perform(get(URI.create(rawPath)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(FirewallRejections.MESSAGE))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawPath);
        assertOneFirewallWarnAndNoError();
    }

    @Test
    @DisplayName("🔴 AC-1 rejected path WITH a valid authorised token → still 400 (rejection fires before authentication)")
    void rejectedPath_withToken_answers400() throws Exception {
        mockMvc.perform(get(URI.create(GET_PATH + ";x"))
                        .header("Authorization", "Bearer " + token(List.of("SCM_LOGISTICS_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        assertOneFirewallWarnAndNoError();
    }

    @Test
    @DisplayName("control — a normal path without a token is still 401 UNAUTHORIZED")
    void normalPath_anonymous_still401() throws Exception {
        mockMvc.perform(get(GET_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("control — an authorised normal request is unchanged (reaches the controller: 404 not-found)")
    void normalPath_authorised_unchanged() throws Exception {
        when(retryDispatchUseCase.retry(any())).thenThrow(new DispatchNotFoundException(DISPATCH_ID));
        mockMvc.perform(post(RETRY_PATH)
                        .header("Authorization", "Bearer " + token(List.of("SCM_LOGISTICS_WRITE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("control — a real unexpected exception is still 500 INTERNAL_ERROR")
    void unexpectedException_still500() throws Exception {
        when(retryDispatchUseCase.retry(any())).thenThrow(new IllegalStateException("boom"));
        mockMvc.perform(post(RETRY_PATH)
                        .header("Authorization", "Bearer " + token(List.of("SCM_LOGISTICS_WRITE"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private void assertOneFirewallWarnAndNoError() {
        List<ILoggingEvent> firewall = logs.list.stream()
                .filter(e -> e.getLoggerName().equals(FirewallRejections.class.getName()))
                .toList();
        assertThat(firewall).hasSize(1);
        assertThat(firewall.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list).noneMatch(e -> e.getLevel() == Level.ERROR);
    }

    private static String token(List<String> roles) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("console-operator-801")
                .issuer(ISSUER)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, "scm")
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("RSA keypair generation failed", e);
        }
    }
}
