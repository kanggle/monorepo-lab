package com.example.notification.adapter.in.rest;

import com.example.common.page.PageResult;
import com.example.notification.application.result.GetNotificationResult;
import com.example.notification.application.result.GetPreferenceResult;
import com.example.notification.application.result.ListNotificationsResult;
import com.example.notification.application.port.in.ManagePreferenceUseCase;
import com.example.notification.application.port.in.QueryNotificationUseCase;
import com.example.notification.domain.exception.NotificationNotFoundException;
import com.example.notification.domain.exception.UnauthorizedNotificationAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@DisplayName("NotificationController 슬라이스 테스트")
class NotificationControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QueryNotificationUseCase notificationQueryService;

    @MockitoBean
    private ManagePreferenceUseCase preferenceService;

    @Test
    @DisplayName("GET /api/notifications/me - 알림 목록 조회 성공")
    void getMyNotifications_returns200() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ListNotificationsResult.NotificationSummary summary = new ListNotificationsResult.NotificationSummary(
                "noti-1", "EMAIL", "Test Subject", "SENT", now, now);

        PageResult<ListNotificationsResult.NotificationSummary> pageResult =
                new PageResult<>(List.of(summary), 0, 20, 1L, 1);
        given(notificationQueryService.getNotifications(eq("user-1"), any()))
                .willReturn(pageResult);

        mockMvc.perform(get("/api/notifications/me")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].notificationId").value("noti-1"))
                .andExpect(jsonPath("$.content[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/notifications/me/{notificationId} - 알림 상세 조회 성공")
    void getNotificationDetail_returns200() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        GetNotificationResult result = new GetNotificationResult(
                "noti-1", "user-1", "EMAIL", "Subject", "Body content", "SENT", now, now);

        given(notificationQueryService.getNotificationDetail("user-1", "noti-1"))
                .willReturn(result);

        mockMvc.perform(get("/api/notifications/me/noti-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value("noti-1"))
                .andExpect(jsonPath("$.body").value("Body content"));
    }

    @Test
    @DisplayName("GET /api/notifications/me/{notificationId} - 존재하지 않는 알림 조회 시 404")
    void getNotificationDetail_notFound_returns404() throws Exception {
        given(notificationQueryService.getNotificationDetail("user-1", "noti-999"))
                .willThrow(new NotificationNotFoundException("noti-999"));

        mockMvc.perform(get("/api/notifications/me/noti-999")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/notifications/me/{notificationId} - 다른 사용자의 알림 조회 시 403")
    void getNotificationDetail_wrongUser_returns403() throws Exception {
        given(notificationQueryService.getNotificationDetail("user-1", "noti-1"))
                .willThrow(new UnauthorizedNotificationAccessException("Not the notification recipient"));

        mockMvc.perform(get("/api/notifications/me/noti-1")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/notifications/me/preferences - 알림 설정 조회 성공")
    void getPreferences_returns200() throws Exception {
        GetPreferenceResult pref = new GetPreferenceResult("user-1", true, false, true);

        given(preferenceService.getPreference("user-1")).willReturn(pref);

        mockMvc.perform(get("/api/notifications/me/preferences")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.smsEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true));
    }

    @Test
    @DisplayName("PUT /api/notifications/me/preferences - 알림 설정 수정 성공")
    void updatePreferences_returns200() throws Exception {
        GetPreferenceResult updated = new GetPreferenceResult("user-1", false, true, false);

        given(preferenceService.updatePreference(any())).willReturn(updated);

        mockMvc.perform(put("/api/notifications/me/preferences")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false,\"smsEnabled\":true,\"pushEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false))
                .andExpect(jsonPath("$.smsEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(false));
    }

    @Test
    @DisplayName("PUT /api/notifications/me/preferences - 필수 필드 누락 시 400")
    void updatePreferences_missingField_returns400() throws Exception {
        mockMvc.perform(put("/api/notifications/me/preferences")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/notifications/me - X-User-Id 헤더 누락 시 401 반환")
    void getMyNotifications_missingUserIdHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/notifications/me - X-User-Id 헤더 빈 문자열 시 400 반환")
    void getMyNotifications_blankUserIdHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/notifications/me")
                        .header("X-User-Id", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/notifications/me/preferences - X-User-Id 헤더 누락 시 401 반환")
    void getPreferences_missingUserIdHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications/me/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PUT /api/notifications/me/preferences - X-User-Id 헤더 누락 시 401 반환")
    void updatePreferences_missingUserIdHeader_returns401() throws Exception {
        mockMvc.perform(put("/api/notifications/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":true,\"smsEnabled\":false,\"pushEnabled\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
