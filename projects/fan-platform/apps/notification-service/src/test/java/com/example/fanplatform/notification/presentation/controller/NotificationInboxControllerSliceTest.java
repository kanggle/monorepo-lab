package com.example.fanplatform.notification.presentation.controller;

import com.example.fanplatform.notification.application.ListNotificationsUseCase;
import com.example.fanplatform.notification.application.MarkNotificationReadUseCase;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationNotFoundException;
import com.example.fanplatform.notification.domain.notification.NotificationPage;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.notification.presentation.filter.TenantClaimEnforcer;
import com.example.fanplatform.notification.testsupport.JwtTestHelper;
import com.example.fanplatform.notification.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationInboxController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class, TenantClaimEnforcer.class})
class NotificationInboxControllerSliceTest {

    private static final JwtTestHelper JWT = new JwtTestHelper();

    @BeforeAll
    static void wireFixture() {
        SliceTestSecurityConfig.useFixture(JWT);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListNotificationsUseCase listNotifications;

    @MockitoBean
    private MarkNotificationReadUseCase markNotificationRead;

    private static Notification sample() {
        return Notification.create("n1", "fan-platform", "acc-1", NotificationType.WELCOME,
                "Welcome to PREMIUM membership", "body", "evt-1",
                "fan.membership.activated", "mem-1", Instant.parse("2026-06-11T08:00:00Z"));
    }

    private String bearer() {
        return "Bearer " + JWT.signFanToken("acc-1");
    }

    @Test
    @DisplayName("GET inbox returns the paginated envelope")
    void listReturnsEnvelope() throws Exception {
        when(listNotifications.list(any(), any(), anyInt(), anyInt()))
                .thenReturn(new NotificationPage(List.of(sample()), 0, 20, 1));

        mockMvc.perform(get("/api/fan/notifications").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("n1"))
                .andExpect(jsonPath("$.data[0].sourceDomain").value("fan"))
                .andExpect(jsonPath("$.data[0].type").value("WELCOME"))
                .andExpect(jsonPath("$.data[0].status").value("UNREAD"))
                .andExpect(jsonPath("$.data[0].deepLink").doesNotExist())
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET inbox ?unread=true maps the normative filter onto status UNREAD (contract § 2.1)")
    void unreadAliasMapsToStatusFilter() throws Exception {
        when(listNotifications.list(any(), eq(NotificationStatus.UNREAD), anyInt(), anyInt()))
                .thenReturn(new NotificationPage(List.of(sample()), 0, 20, 1));

        mockMvc.perform(get("/api/fan/notifications?unread=true").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sourceDomain").value("fan"));

        org.mockito.Mockito.verify(listNotifications)
                .list(any(), eq(NotificationStatus.UNREAD), anyInt(), anyInt());
    }

    @Test
    @DisplayName("POST mark-read returns the updated notification")
    void markReadReturnsOk() throws Exception {
        Notification read = sample();
        read.markRead(Instant.parse("2026-06-11T09:00:00Z"));
        when(markNotificationRead.markRead(any(), eq("n1"))).thenReturn(read);

        mockMvc.perform(post("/api/fan/notifications/n1/read").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"))
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    @DisplayName("cross-account / unknown id → 404 NOTIFICATION_NOT_FOUND")
    void markReadForeignIdIs404() throws Exception {
        when(markNotificationRead.markRead(any(), eq("nX")))
                .thenThrow(new NotificationNotFoundException("nX"));

        mockMvc.perform(post("/api/fan/notifications/nX/read").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("no bearer token → 401")
    void unauthenticatedIs401() throws Exception {
        mockMvc.perform(get("/api/fan/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
