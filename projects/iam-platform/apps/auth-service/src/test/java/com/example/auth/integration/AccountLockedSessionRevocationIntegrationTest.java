package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.session.PrincipalDetailKeys;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.messaging.dedupe.EventDedupePort;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-601 — an {@code account.locked} event revokes the account's sessions, judged by
 * the RESULT: the SAS {@code refresh_token} grant that succeeded before the lock is refused
 * after it (AC-1 authority; the Docker-free {@code :test} cannot run Kafka + MySQL).
 *
 * <p>The session is a real SAS session: authorization_code + PKCE through
 * {@code /oauth2/authorize} with the principal shape the login paths build
 * (name = email, details carry {@code account_id}). That shape is the point — the old
 * {@code revokeAllByAccountId} could not see it.
 *
 * <p>The listener group is overridden to a per-class id so another cached test context's
 * {@code auth-service} consumer cannot take the partition away from this one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("account.locked → 세션 폐기 통합 테스트 — TASK-BE-601")
class AccountLockedSessionRevocationIntegrationTest extends AbstractIntegrationTest {

    private static final String GROUP_ID = "auth-service-it-be601";
    private static final String TOPIC = "account.locked";
    private static final String DLQ = "account.locked.dlq";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // account-service is only reached for fail-soft token claims — every call 404s.
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registry.add("auth.account-service.base-url", wireMock::baseUrl);
        registry.add("auth.kafka.account-locked.group-id", () -> GROUP_ID);
    }

    @BeforeAll
    static void createTopics() throws Exception {
        if (KAFKA == null) {
            return;
        }
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            for (String topic : List.of(TOPIC, DLQ)) {
                try {
                    admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) {
                        throw e;
                    }
                }
            }
        }
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @MockitoBean
    private com.example.security.oauth2.client.IamClientCredentialsTokenProvider gapTokenProvider;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private KafkaListenerEndpointRegistry listenerRegistry;
    @Autowired private OAuth2AuthorizationService authorizationService;
    @Autowired private CredentialJpaRepository credentialJpaRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EventDedupePort eventDedupePort;
    @Autowired private TransactionTemplate transactionTemplate;

    @BeforeEach
    void waitForListener() {
        org.mockito.Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            if (container.getGroupId() != null && container.getGroupId().equals(GROUP_ID)) {
                ContainerTestUtils.waitForAssignment(container, 1);
            }
        }
    }

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("잠금 전 refresh 성공(대조군) → account.locked → 같은 세션의 refresh 거부 · 같은 이메일의 다른 계정은 생존")
    void lockEvent_refusesTheNextRefresh_andSparesAnotherAccountWithTheSameEmail() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String otherAccountId = UUID.randomUUID().toString();
        // A real-shaped email (54 chars). TASK-BE-601 had to shorten it to <= 36 chars because
        // the SAS mirror row wrote the principal name into refresh_tokens.account_id
        // VARCHAR(36); TASK-BE-603 keys that row on the account UUID, so the workaround is
        // reverted. Keep it longer than 36 — a short one would hide that defect again.
        String email = "be601-" + UUID.randomUUID() + "@example.com";
        assertThat(email).hasSizeGreaterThan(36);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                accountId, "fan-platform", email, CredentialHash.argon2id("unused"), Instant.now())));

        String refreshToken = signIn(email, accountId, "fan-platform");
        // The same address owning an account in another tenant — must survive the lock.
        String othersRefreshToken = signIn(email, otherAccountId, "ecommerce");

        // Control: before the lock, the session refreshes.
        String rotated = refresh(refreshToken);

        publish(accountId, flatLockedEvent(UUID.randomUUID(), accountId, "fan-platform"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            OAuth2Authorization authorization = authorizationService.findByToken(rotated, OAuth2TokenType.REFRESH_TOKEN);
            assertThat(authorization).isNotNull();
            assertThat(authorization.getRefreshToken().isActive()).isFalse();
        });

        // The result the ticket is about: the pre-lock session can no longer refresh.
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", rotated)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        OAuth2Authorization others = authorizationService.findByToken(othersRefreshToken, OAuth2TokenType.REFRESH_TOKEN);
        assertThat(others).isNotNull();
        assertThat(others.getRefreshToken().isActive())
                .as("an account in another tenant that shares the email must keep its session")
                .isTrue();
    }

    @Test
    @DisplayName("tenantId 없는 봉투 → account.locked.dlq 로 간다 (재시도 없이)")
    void missingTenant_isDeadLettered() {
        String key = "be601-dlq-" + UUID.randomUUID();
        publish(key, "{\"eventId\":\"" + UUID.randomUUID() + "\",\"accountId\":\"" + key
                + "\",\"reasonCode\":\"ADMIN_LOCK\"}");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "be601-dlq-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> reader = new KafkaConsumer<>(props)) {
            reader.subscribe(List.of(DLQ));
            AtomicInteger found = new AtomicInteger();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                ConsumerRecords<String, String> records = reader.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    if (key.equals(r.key())) {
                        found.incrementAndGet();
                    }
                }
                return found.get() > 0;
            });
        }
    }

    @Test
    @DisplayName("processed_events dedupe (실제 MySQL): 첫 번째 APPLIED · 같은 id 두 번째 IGNORED · 실패는 롤백되어 재처리 가능")
    void dedupe_onRealMysql() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger runs = new AtomicInteger();

        EventDedupePort.Outcome first = transactionTemplate.execute(s ->
                eventDedupePort.process(eventId, TOPIC, runs::incrementAndGet));
        EventDedupePort.Outcome second = transactionTemplate.execute(s ->
                eventDedupePort.process(eventId, TOPIC, runs::incrementAndGet));

        assertThat(first).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(second).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(runs).hasValue(1);

        UUID failing = UUID.randomUUID();
        assertThatThrownBy(() -> transactionTemplate.execute(s ->
                eventDedupePort.process(failing, TOPIC, () -> {
                    throw new IllegalStateException("revoke failed");
                })))
                .isInstanceOf(IllegalStateException.class);
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Integer.class, failing.toString());
        assertThat(rows).as("a failed revoke must not leave the event marked processed").isZero();
    }

    // -----------------------------------------------------------------------

    private void publish(String key, String value) {
        try {
            kafkaTemplate.send(TOPIC, key, value).get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String flatLockedEvent(UUID eventId, String accountId, String tenantId) {
        return "{\"eventId\":\"" + eventId + "\",\"accountId\":\"" + accountId + "\",\"tenantId\":\""
                + tenantId + "\",\"reasonCode\":\"ADMIN_LOCK\",\"actorType\":\"operator\",\"lockedAt\":\""
                + Instant.now() + "\"}";
    }

    /** authorization_code + PKCE with the principal shape the form/social login paths build. */
    private String signIn(String email, String accountId, String tenantId) throws Exception {
        Map<String, Object> details = new HashMap<>();
        details.put(PrincipalDetailKeys.TENANT_ID, tenantId);
        details.put(PrincipalDetailKeys.TENANT_TYPE, "B2C");
        details.put(PrincipalDetailKeys.ACCOUNT_ID, accountId);
        details.put(PrincipalDetailKeys.EMAIL, email);
        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        principal.setDetails(details);

        String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
        String codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .with(authentication(principal))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "demo-spa-client")
                        .queryParam("redirect_uri", "http://localhost:3000/callback")
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getHeader("Location");
        assertThat(location).isNotNull().contains("code=");
        String code = location.substring(location.indexOf("code=") + 5).split("&")[0];

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(token.getResponse().getContentAsString());
        return body.get("refresh_token").asText();
    }

    private String refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken)
                        .param("client_id", "demo-spa-client"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("refresh_token").asText();
    }
}
