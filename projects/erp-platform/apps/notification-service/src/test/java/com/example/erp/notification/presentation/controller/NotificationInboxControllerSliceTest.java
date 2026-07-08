package com.example.erp.notification.presentation.controller;

import com.example.erp.notification.application.MarkNotificationReadUseCase;
import com.example.erp.notification.application.QueryInboxUseCase;
import com.example.erp.notification.application.query.InboxPage;
import com.example.erp.notification.config.SecurityConfig;
import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.presentation.advice.GlobalExceptionHandler;
import com.example.erp.notification.presentation.filter.TenantClaimEnforcer;
import com.example.erp.notification.presentation.security.ReadAccessDeniedException;
import com.example.erp.notification.presentation.security.ReadAuthorizationGate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link NotificationInboxController} + the
 * {@link SecurityConfig} chain + {@link GlobalExceptionHandler} error envelope.
 * The {@code jwt()} post-processor drives the real filter chain so
 * {@code @AuthenticationPrincipal Jwt} resolves; the READ gate is mocked. Asserts
 * recipient-scoping (sub flows to the use case), NON_NULL readAt, the 404
 * NOTIFICATION_NOT_FOUND path, and the mark-read shape.
 */
@WebMvcTest(NotificationInboxController.class)
@Import({SecurityConfig.class, TenantClaimEnforcer.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "erpplatform.oauth2.required-tenant-id=erp")
class NotificationInboxControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryInboxUseCase queryInbox;
    @MockitoBean
    MarkNotificationReadUseCase markRead;
    @MockitoBean
    ReadAuthorizationGate readGate;

    private static RequestPostProcessor caller(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("tenant_id", "erp").claim("scope", "erp.read"));
    }

    private Notification unread() {
        return Notification.create("ntf-1", "erp", "emp-1", NotificationType.APPROVAL_SUBMITTED,
                "결재 요청 도착", "body", SourceRef.approval("appr-1"),
                Instant.parse("2026-06-05T10:00:00Z"));
    }

    private Notification read() {
        Notification n = unread();
        n.markRead(Instant.parse("2026-06-05T11:00:00Z"));
        return n;
    }

    @Test
    void listIsRecipientScopedToSub() throws Exception {
        when(queryInbox.list(eq("erp"), eq("emp-1"), any(), eq(0), eq(20)))
                .thenReturn(new InboxPage(List.of(unread()), 0, 20, 1L));

        mockMvc.perform(get("/api/erp/notifications").with(caller("emp-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("ntf-1"))
                // ADR-MONO-043 P2: contract § 1 sourceDomain present + always "erp".
                .andExpect(jsonPath("$.data[0].sourceDomain").value("erp"))
                // deepLink derived from APPROVAL source → 결재함 route (TASK-ERP-BE-028).
                .andExpect(jsonPath("$.data[0].deepLink").value("/erp/approval?request=appr-1"))
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void deepLinkDerivedForDelegationSource() throws Exception {
        Notification delegation = Notification.create("ntf-2", "erp", "emp-1",
                NotificationType.DELEGATION_GRANTED, "결재 위임 지정", "body",
                SourceRef.delegation("grant-9"), Instant.parse("2026-06-05T10:00:00Z"));
        when(queryInbox.list(eq("erp"), eq("emp-1"), any(), eq(0), eq(20)))
                .thenReturn(new InboxPage(List.of(delegation), 0, 20, 1L));

        mockMvc.perform(get("/api/erp/notifications").with(caller("emp-1")))
                .andExpect(status().isOk())
                // DELEGATION source → 위임 route (TASK-ERP-BE-028).
                .andExpect(jsonPath("$.data[0].deepLink").value("/erp/delegation"))
                .andExpect(jsonPath("$.data[0].sourceType").value("DELEGATION"))
                .andExpect(jsonPath("$.data[0].sourceId").value("grant-9"));
    }

    @Test
    void getOneOwnReturnsNotification() throws Exception {
        when(queryInbox.getOne("erp", "emp-1", "ntf-1")).thenReturn(unread());
        mockMvc.perform(get("/api/erp/notifications/ntf-1").with(caller("emp-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ntf-1"))
                .andExpect(jsonPath("$.data.sourceDomain").value("erp"))
                .andExpect(jsonPath("$.data.sourceId").value("appr-1"));
    }

    @Test
    void getOneForeignReturns404() throws Exception {
        when(queryInbox.getOne("erp", "emp-1", "ntf-9"))
                .thenThrow(new NotificationNotFoundException("ntf-9"));
        mockMvc.perform(get("/api/erp/notifications/ntf-9").with(caller("emp-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void markReadReturnsReadAt() throws Exception {
        when(markRead.markRead("erp", "emp-1", "ntf-1")).thenReturn(read());
        mockMvc.perform(post("/api/erp/notifications/ntf-1/read").with(caller("emp-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").exists());
    }

    @Test
    void readGateDenialReturns403() throws Exception {
        doThrow(new ReadAccessDeniedException("no read")).when(readGate).requireRead(any());
        mockMvc.perform(get("/api/erp/notifications").with(caller("emp-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void badSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/erp/notifications?size=999").with(caller("emp-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
