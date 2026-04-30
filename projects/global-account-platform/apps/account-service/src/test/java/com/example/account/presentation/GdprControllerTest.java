package com.example.account.presentation;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.DataExportResult;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.service.DataExportUseCase;
import com.example.account.application.service.GdprDeleteUseCase;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.GdprController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GdprController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("GdprController 슬라이스 테스트")
class GdprControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GdprDeleteUseCase gdprDeleteUseCase;

    @MockitoBean
    private DataExportUseCase dataExportUseCase;

    private static final String ACCOUNT_ID = "acc-1";
    private static final String EMAIL_HASH =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String GDPR_DELETE_BODY = """
            {
              "reason": "REGULATED_DELETION",
              "operatorId": "op-1"
            }
            """;

    @Test
    @DisplayName("POST /internal/accounts/{id}/gdpr-delete — 정상 처리 시 200 과 GdprDeleteResponse 반환")
    void gdprDelete_validRequest_returns200WithMaskedPayload() throws Exception {
        Instant maskedAt = Instant.parse("2026-04-18T10:00:00Z");
        given(gdprDeleteUseCase.execute(eq(ACCOUNT_ID), eq("op-1")))
                .willReturn(new GdprDeleteResult(ACCOUNT_ID, "DELETED", EMAIL_HASH, maskedAt));

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GDPR_DELETE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.emailHash").value(EMAIL_HASH))
                .andExpect(jsonPath("$.maskedAt").exists());
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/gdpr-delete — 이미 DELETED 상태이면 409 STATE_TRANSITION_INVALID")
    void gdprDelete_alreadyDeleted_returns409StateTransitionInvalid() throws Exception {
        given(gdprDeleteUseCase.execute(eq(ACCOUNT_ID), eq("op-1")))
                .willThrow(new StateTransitionException(
                        AccountStatus.DELETED, AccountStatus.DELETED,
                        StatusChangeReason.REGULATED_DELETION));

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", ACCOUNT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GDPR_DELETE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/gdpr-delete — 계정 미존재 시 404 ACCOUNT_NOT_FOUND")
    void gdprDelete_accountNotFound_returns404() throws Exception {
        given(gdprDeleteUseCase.execute(eq("acc-999"), eq("op-1")))
                .willThrow(new AccountNotFoundException("acc-999"));

        mockMvc.perform(post("/internal/accounts/{id}/gdpr-delete", "acc-999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GDPR_DELETE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /internal/accounts/{id}/export — 정상 처리 시 200 과 DataExportResponse 반환")
    void export_validRequest_returns200WithAccountAndProfile() throws Exception {
        DataExportResult.ProfileData profile = new DataExportResult.ProfileData(
                "John Doe", "+82-10-1234-5678",
                LocalDate.of(1990, 1, 15),
                "ko-KR", "Asia/Seoul");
        DataExportResult result = new DataExportResult(
                ACCOUNT_ID,
                "user@example.com",
                "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"),
                profile,
                Instant.parse("2026-04-18T10:00:00Z"));

        given(dataExportUseCase.execute(eq(ACCOUNT_ID))).willReturn(result);

        mockMvc.perform(get("/internal/accounts/{id}/export", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.profile.displayName").value("John Doe"))
                .andExpect(jsonPath("$.profile.phoneNumber").value("+82-10-1234-5678"))
                .andExpect(jsonPath("$.profile.birthDate").value("1990-01-15"))
                .andExpect(jsonPath("$.profile.locale").value("ko-KR"))
                .andExpect(jsonPath("$.profile.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.exportedAt").exists());
    }

    @Test
    @DisplayName("GET /internal/accounts/{id}/export — 계정 미존재 시 404 ACCOUNT_NOT_FOUND")
    void export_accountNotFound_returns404() throws Exception {
        given(dataExportUseCase.execute(eq("acc-999")))
                .willThrow(new AccountNotFoundException("acc-999"));

        mockMvc.perform(get("/internal/accounts/{id}/export", "acc-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
