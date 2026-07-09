package com.example.auth.presentation;

import com.example.auth.application.BackfillCredentialIdentityUseCase;
import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.ResolveCredentialAccountIdUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.exception.CredentialAlreadyExistsException;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// TASK-BE-487: /internal/auth/** is now JWT-only (fail-closed). These slice tests exercise the
// controller behaviour, not the auth gate, so the dev/test bypass is enabled to reach the handler
// without minting a real GAP client_credentials JWT (the fail-closed 401 path is covered by
// InternalCredentialAuthSliceTest).
@WebMvcTest(InternalCredentialController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("InternalCredentialController slice tests")
class InternalCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCredentialUseCase createCredentialUseCase;

    @MockitoBean
    private ForceLogoutUseCase forceLogoutUseCase;

    @MockitoBean
    private BackfillCredentialIdentityUseCase backfillCredentialIdentityUseCase;

    @MockitoBean
    private ResolveCredentialAccountIdUseCase resolveCredentialAccountIdUseCase;

    // ── TASK-MONO-298 (ADR-MONO-040 Phase 3 part A): email → account_id resolution ──

    @Test
    @DisplayName("POST /internal/auth/credentials/account-id-by-email — 매칭 → 200 {accountId}")
    void resolveAccountIdByEmail_match_returns200() throws Exception {
        given(resolveCredentialAccountIdUseCase.resolveAccountId("operator@example.com", "acme-corp"))
                .willReturn(Optional.of("01928c4a-7e9f-7c00-9a40-d2b1f5e8c200"));

        mockMvc.perform(post("/internal/auth/credentials/account-id-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "operator@example.com", "tenantId": "acme-corp" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("01928c4a-7e9f-7c00-9a40-d2b1f5e8c200"));
    }

    @Test
    @DisplayName("POST /internal/auth/credentials/account-id-by-email — 미매칭 → 200 {accountId:null} (fail-soft)")
    void resolveAccountIdByEmail_noMatch_returns200WithNull() throws Exception {
        given(resolveCredentialAccountIdUseCase.resolveAccountId("ghost@example.com", "ghost-corp"))
                .willReturn(Optional.empty());

        mockMvc.perform(post("/internal/auth/credentials/account-id-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "ghost@example.com", "tenantId": "ghost-corp" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("POST /internal/auth/credentials/account-id-by-email — blank email → 400 VALIDATION_ERROR (no PII in URL)")
    void resolveAccountIdByEmail_blankEmail_returns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials/account-id-by-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "", "tenantId": "acme-corp" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /internal/auth/credentials/identity-backfill — items → use case → {requested, updated} (TASK-BE-386)")
    void backfillIdentity_returnsCounts() throws Exception {
        given(backfillCredentialIdentityUseCase.execute(any()))
                .willReturn(new BackfillCredentialIdentityUseCase.Result(2, 1));

        mockMvc.perform(post("/internal/auth/credentials/identity-backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "accountId": "acc-1", "identityId": "idy-1" },
                                    { "accountId": "acc-2", "identityId": "idy-2" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.updated").value(1));

        verify(backfillCredentialIdentityUseCase).execute(any());
    }

    @Test
    @DisplayName("POST /internal/auth/credentials/identity-backfill — empty items → 400 VALIDATION_ERROR")
    void backfillIdentity_emptyItems_returns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials/identity-backfill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /internal/auth/credentials happy path returns 201")
    void createCredential_returns201() throws Exception {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willReturn(new CreateCredentialResult("acc-1", createdAt));

        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-19T10:00:00Z"));

        ArgumentCaptor<CreateCredentialCommand> captor =
                ArgumentCaptor.forClass(CreateCredentialCommand.class);
        verify(createCredentialUseCase).execute(captor.capture());
        CreateCredentialCommand cmd = captor.getValue();
        assertThat(cmd.accountId()).isEqualTo("acc-1");
        assertThat(cmd.email()).isEqualTo("user@example.com");
        assertThat(cmd.password()).isEqualTo("password123");
    }

    @Test
    @DisplayName("POST /internal/auth/credentials — tenantId is mapped onto the command")
    void createCredential_carriesTenantId() throws Exception {
        Instant createdAt = Instant.parse("2026-06-02T10:00:00Z");
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willReturn(new CreateCredentialResult("acc-op", createdAt));

        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-op",
                                  "email": "operator@example.com",
                                  "password": "password123",
                                  "tenantId": "acme-corp"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateCredentialCommand> captor =
                ArgumentCaptor.forClass(CreateCredentialCommand.class);
        verify(createCredentialUseCase).execute(captor.capture());
        CreateCredentialCommand cmd = captor.getValue();
        // TASK-MONO-263: accountType field is gone; tenantId still maps through.
        assertThat(cmd.tenantId()).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("TASK-MONO-263: an accountType field in the body is ignored (201, no validation error)")
    void accountTypeFieldIgnored() throws Exception {
        Instant createdAt = Instant.parse("2026-06-02T10:00:00Z");
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willReturn(new CreateCredentialResult("acc-1", createdAt));

        // accountType is no longer a request field — an unknown property is ignored
        // (Jackson default FAIL_ON_UNKNOWN_PROPERTIES=false), so this still succeeds.
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "password123",
                                  "accountType": "SUPERUSER"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /internal/auth/credentials — idempotent retry returns 200 OK (TASK-BE-247)")
    void idempotentRetry_returns200() throws Exception {
        Instant createdAt = Instant.parse("2026-04-30T10:00:00Z");
        // wasIdempotent=true → use-case signals this is a retry of an existing row
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willReturn(new CreateCredentialResult("acc-1", createdAt, true));

        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-30T10:00:00Z"));
    }

    @Test
    @DisplayName("POST /internal/auth/credentials with duplicate accountId returns 409 CREDENTIAL_ALREADY_EXISTS")
    void duplicateAccountReturns409() throws Exception {
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willThrow(new CredentialAlreadyExistsException("acc-1"));

        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Missing accountId → 400 VALIDATION_ERROR")
    void missingAccountIdReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Invalid email format → 400 VALIDATION_ERROR")
    void invalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "not-an-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Password shorter than 8 chars → 400 VALIDATION_ERROR")
    void shortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Malformed JSON → 400 VALIDATION_ERROR")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
