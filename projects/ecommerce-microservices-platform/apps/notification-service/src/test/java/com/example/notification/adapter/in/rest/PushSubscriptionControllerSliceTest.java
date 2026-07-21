package com.example.notification.adapter.in.rest;

import com.example.notification.application.command.RegisterPushSubscriptionCommand;
import com.example.notification.application.port.in.ManagePushSubscriptionUseCase;
import com.example.notification.application.result.RegisterSubscriptionResult;
import com.example.notification.domain.exception.PushNotConfiguredException;
import com.example.notification.domain.model.PushSubscription;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PushSubscriptionController.class)
@DisplayName("PushSubscriptionController 슬라이스 테스트")
class PushSubscriptionControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ManagePushSubscriptionUseCase pushSubscriptionService;

    private String body(String endpoint) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "endpoint", endpoint,
                "keys", Map.of("p256dh", "p256", "auth", "auth")));
    }

    @Test
    @DisplayName("GET /vapid-public-key - 설정 시 200 + publicKey")
    void vapidPublicKey_200() throws Exception {
        given(pushSubscriptionService.getVapidPublicKey()).willReturn("BPk123");

        mockMvc.perform(get("/api/notifications/vapid-public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("BPk123"));
    }

    @Test
    @DisplayName("GET /vapid-public-key - 미설정 시 503 PUSH_NOT_CONFIGURED")
    void vapidPublicKey_503() throws Exception {
        given(pushSubscriptionService.getVapidPublicKey()).willThrow(new PushNotConfiguredException());

        mockMvc.perform(get("/api/notifications/vapid-public-key"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PUSH_NOT_CONFIGURED"));
    }

    @Test
    @DisplayName("POST /me/push-subscriptions - 신규 구독은 201")
    void register_new_201() throws Exception {
        given(pushSubscriptionService.register(any()))
                .willReturn(new RegisterSubscriptionResult("sub-1", true));

        mockMvc.perform(post("/api/notifications/me/push-subscriptions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://push/ep")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subscriptionId").value("sub-1"));

        verify(pushSubscriptionService).register(any(RegisterPushSubscriptionCommand.class));
    }

    @Test
    @DisplayName("POST /me/push-subscriptions - 요청의 User-Agent 를 커맨드로 전달한다")
    void register_capturesUserAgent() throws Exception {
        given(pushSubscriptionService.register(any()))
                .willReturn(new RegisterSubscriptionResult("sub-1", true));

        mockMvc.perform(post("/api/notifications/me/push-subscriptions")
                        .header("X-User-Id", "user-1")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://push/ep")))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterPushSubscriptionCommand> captor =
                ArgumentCaptor.forClass(RegisterPushSubscriptionCommand.class);
        verify(pushSubscriptionService).register(captor.capture());
        assertThat(captor.getValue().userAgent()).isEqualTo("Mozilla/5.0 (Windows NT 10.0) Chrome/120");
    }

    @Test
    @DisplayName("GET /me/push-subscriptions - 200 + 구독 목록(키는 미노출)")
    void list_200_noKeys() throws Exception {
        PushSubscription sub = PushSubscription.reconstitute(
                "sub-1", "ecommerce", "user-1", "https://push/ep", "secret-p256", "secret-auth",
                "Mozilla/5.0 (Windows NT 10.0) Chrome/120",
                LocalDateTime.of(2026, 7, 2, 9, 0), LocalDateTime.of(2026, 7, 2, 9, 0));
        given(pushSubscriptionService.listByUser("user-1")).willReturn(List.of(sub));

        mockMvc.perform(get("/api/notifications/me/push-subscriptions")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions[0].id").value("sub-1"))
                .andExpect(jsonPath("$.subscriptions[0].endpoint").value("https://push/ep"))
                .andExpect(jsonPath("$.subscriptions[0].userAgent").value("Mozilla/5.0 (Windows NT 10.0) Chrome/120"))
                .andExpect(jsonPath("$.subscriptions[0].createdAt").exists())
                // Key material must never be serialized.
                .andExpect(jsonPath("$.subscriptions[0].p256dh").doesNotExist())
                .andExpect(jsonPath("$.subscriptions[0].auth").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/push-subscriptions - X-User-Id 누락 시 401")
    void list_missingUser_401() throws Exception {
        mockMvc.perform(get("/api/notifications/me/push-subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /me/push-subscriptions - 기존 endpoint 갱신은 200")
    void register_refresh_200() throws Exception {
        given(pushSubscriptionService.register(any()))
                .willReturn(new RegisterSubscriptionResult("sub-1", false));

        mockMvc.perform(post("/api/notifications/me/push-subscriptions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://push/ep")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptionId").value("sub-1"));
    }

    @Test
    @DisplayName("POST /me/push-subscriptions - X-User-Id 누락 시 401")
    void register_missingUser_401() throws Exception {
        mockMvc.perform(post("/api/notifications/me/push-subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://push/ep")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /me/push-subscriptions - 빈 endpoint 는 400")
    void register_blankEndpoint_400() throws Exception {
        mockMvc.perform(post("/api/notifications/me/push-subscriptions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("DELETE /me/push-subscriptions - 204 + 서비스 위임")
    void unregister_204() throws Exception {
        mockMvc.perform(delete("/api/notifications/me/push-subscriptions")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("endpoint", "https://push/ep"))))
                .andExpect(status().isNoContent());

        verify(pushSubscriptionService).unregister(eq("user-1"), eq("https://push/ep"));
    }
}
