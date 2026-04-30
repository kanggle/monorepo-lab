package com.example.account.integration;

import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Account 가입/상태변경/이력 통합 테스트")
class AccountSignupIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> "test-internal-token");
    }

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountStatusHistoryRepository historyRepository;

    // TASK-BE-063: signup now calls auth-service /internal/auth/credentials. The
    // integration here focuses on account-service persistence, so we stub the
    // outbound call with a no-op mock.
    @MockitoBean
    private AuthServicePort authServicePort;

    // TASK-BE-063 post-CI: without a KafkaContainer the first signup hangs ~50s on
    // producer metadata lookup. Signup itself only writes to the outbox table; the
    // KafkaTemplate and outbox poller are bean-wired but never needed in this test,
    // so stubbing both removes the hidden Kafka dependency from context startup.
    @MockitoBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @MockitoBean
    private OutboxPollingScheduler outboxPollingScheduler;

    @Test
    @DisplayName("회원가입 후 계정이 ACTIVE 상태로 생성된다")
    void signup_createsActiveAccount() throws Exception {
        String uniqueEmail = "signup-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(uniqueEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountId").exists());

        Optional<Account> saved = accountRepository.findByEmail(TenantId.FAN_PLATFORM, uniqueEmail);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("중복 이메일 가입 시 409 반환")
    void signup_duplicateEmail_returns409() throws Exception {
        String uniqueEmail = "dup-" + UUID.randomUUID() + "@example.com";

        // First signup
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(uniqueEmail)))
                .andExpect(status().isCreated());

        // Second signup with same email
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(uniqueEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("가입 후 잠금 후 이력이 기록된다")
    void signup_thenLock_historyRecorded() throws Exception {
        String uniqueEmail = "lock-" + UUID.randomUUID() + "@example.com";

        // Signup
        var result = mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(uniqueEmail)))
                .andExpect(status().isCreated())
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accountId");

        // Lock account
        mockMvc.perform(post("/internal/accounts/" + accountId + "/lock")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_LOCK",
                                  "operatorId": "op-admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        // Verify history
        List<AccountStatusHistoryEntry> history =
                historyRepository.findByAccountIdOrderByOccurredAtDesc(accountId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getFromStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(history.get(0).getToStatus()).isEqualTo(AccountStatus.LOCKED);

        // Verify account status
        Account account = accountRepository.findById(TenantId.FAN_PLATFORM, accountId).orElseThrow();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    @DisplayName("DELETED 계정에 대한 LOCK 요청이 409를 반환한다")
    void lockDeletedAccount_returns409() throws Exception {
        String uniqueEmail = "del-lock-" + UUID.randomUUID() + "@example.com";

        // Signup
        var result = mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(uniqueEmail)))
                .andExpect(status().isCreated())
                .andReturn();

        String accountId = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.accountId");

        // Delete account
        mockMvc.perform(delete("/api/accounts/me")
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isAccepted());

        // Try to lock deleted account
        mockMvc.perform(post("/internal/accounts/" + accountId + "/lock")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_LOCK",
                                  "operatorId": "op-admin"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }
}
