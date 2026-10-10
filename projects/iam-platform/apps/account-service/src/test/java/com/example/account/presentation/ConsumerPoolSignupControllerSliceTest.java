package com.example.account.presentation;

import com.example.account.application.command.ConsumerPoolSignupCommand;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.ConsumerPoolDisabledException;
import com.example.account.application.result.SignupResult;
import com.example.account.application.service.ConsumerPoolSignupUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.dto.request.ConsumerPoolSignupRequest;
import com.example.account.presentation.internal.ConsumerPoolSignupController;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S3 — {@code POST /internal/consumer-pool/signups} (auth-to-account.md): 201 shape, the
 * consumer-signup validation, the refusal codes, no tenant read from the request.
 */
@WebMvcTest(ConsumerPoolSignupController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("ConsumerPoolSignupController slice (TASK-MONO-772 S3)")
class ConsumerPoolSignupControllerSliceTest {

    private static final String PATH = "/internal/consumer-pool/signups";
    private static final String BODY = """
            {"email":"invitee@example.com","password":"Password1!","displayName":"피초대자"}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ConsumerPoolSignupUseCase useCase;

    @Test
    @DisplayName("201 {accountId, email, status, createdAt} · X-Tenant-Id 를 보내도 명령엔 테넌트가 없다")
    void created() throws Exception {
        given(useCase.execute(any())).willReturn(new SignupResult(
                "acc-1", "invitee@example.com", "ACTIVE", Instant.parse("2026-10-10T10:00:00Z")));

        mockMvc.perform(post(PATH).header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.email").value("invitee@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-10T10:00:00Z"));

        ArgumentCaptor<ConsumerPoolSignupCommand> cmd = ArgumentCaptor.forClass(ConsumerPoolSignupCommand.class);
        verify(useCase).execute(cmd.capture());
        assertThat(cmd.getValue().email()).isEqualTo("invitee@example.com");
        assertThat(cmd.getValue().displayName()).isEqualTo("피초대자");
    }

    @Test
    @DisplayName("형식 오류(이메일 · 8자 미만) → 4xx VALIDATION_ERROR(소비자 가입과 같은 status) · 유스케이스 호출 없음")
    void invalid() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"Password1!\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"short\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("거절: 이미 있음(풀 · 사이트 공존) 409 ACCOUNT_ALREADY_EXISTS · 풀 꺼짐 409 CONSUMER_POOL_DISABLED")
    void refusals() throws Exception {
        given(useCase.execute(any()))
                .willThrow(new AccountAlreadyExistsException("invitee@example.com"))
                .willThrow(new ConsumerPoolDisabledException());

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSUMER_POOL_DISABLED"));
    }

    @Test
    @DisplayName("🔴 R4: 요청 레코드 toString 은 비밀번호 · 이메일을 찍지 않는다")
    void requestToString_redacts() {
        String s = new ConsumerPoolSignupRequest("invitee@example.com", "Password1!", "x", null, null).toString();
        assertThat(s).doesNotContain("Password1!").doesNotContain("invitee@example.com");
    }
}
