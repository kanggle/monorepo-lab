package com.example.notification.adapter.in.rest;

import com.example.notification.application.command.RegisterPushSubscriptionCommand;
import com.example.notification.application.port.in.ManagePushSubscriptionUseCase;
import com.example.notification.application.result.RegisterSubscriptionResult;
import com.example.notification.domain.exception.PushNotConfiguredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PushSubscriptionController.class)
@DisplayName("PushSubscriptionController 슬라이스 테스트")
class PushSubscriptionControllerTest {

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
