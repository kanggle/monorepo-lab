package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationAlreadyPendingException;
import com.example.admin.application.exception.OperatorInvitationNotPendingException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-772 S2 — the transactional half: R4 (only the hash reaches the row, the raw token only the caller),
 * the two conflicts, the idempotency key on the audit row, and the conditional updates' lost races.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorInvitationWriter — 초대 행 쓰기 (TASK-MONO-772 S2)")
class OperatorInvitationWriterTest {

    private static final OperatorContext ACTOR = new OperatorContext("op-admin-x", "jti-1");
    private static final String INV = "0199de70-0000-7000-8000-00000000inv1";

    @Mock OperatorInvitationPort invitationPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock AdminActionAuditor auditor;
    OperatorInvitationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OperatorInvitationWriter(invitationPort, operatorPort, auditor, "P7D");
    }

    private static AdminOperatorPort.OperatorView actorRow() {
        return new AdminOperatorPort.OperatorView(11L, "op-admin-x", "acme-corp", "admin@acme.example", null,
                "김관리", "ACTIVE", null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }

    private static InvitationView view(String status, int version, String hash) {
        return new InvitationView(7L, INV, "acme-corp", "person@example.com", "홍길동", List.of("SUPPORT_LOCK"),
                status, Instant.now().plus(Duration.ofDays(7)), 11L, "op-admin-x", null, null, null, null, null,
                Instant.EPOCH, version, hash);
    }

    @Test
    @DisplayName("ttl: 기본 P7D · 0 이하는 기동 실패")
    void ttl() {
        assertThat(writer.ttl()).isEqualTo(Duration.ofDays(7));
        assertThatThrownBy(() -> new OperatorInvitationWriter(invitationPort, operatorPort, auditor, "PT0S"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("🔴 R4: 행에는 SHA-256(token) 만 · 원문은 호출자에게만 · 만료 = 지금 + P7D · 감사 키 = 클라이언트 키")
    void createPending_storesHashOnly() {
        given(operatorPort.existsByTenantIdAndEmail("acme-corp", "person@example.com")).willReturn(false);
        given(invitationPort.existsPending("acme-corp", "person@example.com")).willReturn(false);
        given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(actorRow()));
        given(invitationPort.create(any())).willAnswer(inv -> {
            OperatorInvitationPort.NewInvitation row = inv.getArgument(0);
            return new InvitationView(7L, row.invitationId(), row.tenantId(), row.email(), row.displayName(),
                    row.roles(), "PENDING", row.expiresAt(), 11L, "op-admin-x", null, null, null, null, null,
                    row.now(), 0, row.tokenHash());
        });
        given(auditor.newAuditId()).willReturn("audit-1");

        Instant before = Instant.now();
        OperatorInvitationWriter.Issued issued = writer.createPending(ACTOR, "acme-corp", "person@example.com",
                "홍길동", List.of("SUPPORT_LOCK"), "k-1", "onboarding");

        ArgumentCaptor<OperatorInvitationPort.NewInvitation> row =
                ArgumentCaptor.forClass(OperatorInvitationPort.NewInvitation.class);
        verify(invitationPort).create(row.capture());
        assertThat(row.getValue().tokenHash()).isEqualTo(OperatorInvitationTokens.sha256Hex(issued.token()));
        assertThat(row.getValue().tokenHash()).doesNotContain(issued.token());
        assertThat(row.getValue().invitedByInternalId()).isEqualTo(11L);
        assertThat(row.getValue().expiresAt()).isBetween(before.plus(Duration.ofDays(7)),
                Instant.now().plus(Duration.ofDays(7)));
        assertThat(issued.toString()).doesNotContain(issued.token());

        ArgumentCaptor<AdminActionAuditor.AuditRecord> audit = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(audit.capture());
        assertThat(audit.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_INVITATION_CREATE);
        assertThat(audit.getValue().targetType()).isEqualTo("OPERATOR_INVITATION");
        assertThat(audit.getValue().targetId()).isEqualTo(issued.invitation().invitationId());
        assertThat(audit.getValue().idempotencyKey()).isEqualTo("k-1");
        assertThat(audit.getValue().targetTenantId()).isEqualTo("acme-corp");
        assertThat(String.valueOf(audit.getValue().downstreamDetail()))
                .doesNotContain(issued.token()).doesNotContain("person@example.com");
    }

    @Test
    @DisplayName("이미 그 테넌트의 운영자 → 409 OPERATOR_EMAIL_CONFLICT · 대기 초대 있음 → 409 ALREADY_PENDING (쓰기 없음)")
    void createPending_conflicts() {
        given(operatorPort.existsByTenantIdAndEmail("acme-corp", "a@x.example")).willReturn(true);
        assertThatThrownBy(() -> writer.createPending(ACTOR, "acme-corp", "a@x.example", "A", List.of(), "k", "r"))
                .isInstanceOf(OperatorEmailConflictException.class);

        given(operatorPort.existsByTenantIdAndEmail("acme-corp", "b@x.example")).willReturn(false);
        given(invitationPort.existsPending("acme-corp", "b@x.example")).willReturn(true);
        assertThatThrownBy(() -> writer.createPending(ACTOR, "acme-corp", "b@x.example", "B", List.of(), "k", "r"))
                .isInstanceOf(OperatorInvitationAlreadyPendingException.class);
        verify(invitationPort, never()).create(any());
    }

    @Test
    @DisplayName("cancel: 동시 취소에 졌으면 no-op(false, 감사 없음) · 동시 수락에 졌으면 409")
    void cancel_lostRaces() {
        given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(actorRow()));
        given(invitationPort.cancelIfPending(eq(7L), eq(11L), any())).willReturn(false);
        given(invitationPort.findByInvitationId(INV))
                .willReturn(Optional.of(view("CANCELLED", 1, "h")))
                .willReturn(Optional.of(view("ACCEPTED", 1, "h")));

        assertThat(writer.cancel(ACTOR, view("PENDING", 0, "h"), "r")).isFalse();
        assertThatThrownBy(() -> writer.cancel(ACTOR, view("PENDING", 0, "h"), "r"))
                .isInstanceOf(OperatorInvitationNotPendingException.class);
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("cancel 성공 → 감사 행 OPERATOR_INVITATION_CANCEL")
    void cancel_ok() {
        given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(actorRow()));
        given(invitationPort.cancelIfPending(eq(7L), eq(11L), any())).willReturn(true);
        given(auditor.newAuditId()).willReturn("audit-c");
        assertThat(writer.cancel(ACTOR, view("PENDING", 0, "h"), "r")).isTrue();
        ArgumentCaptor<AdminActionAuditor.AuditRecord> audit = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(audit.capture());
        assertThat(audit.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_INVITATION_CANCEL);
    }

    @Test
    @DisplayName("🔴 rotate: 새 해시는 새 원문의 해시이고 옛 해시와 다르다 · 읽은 version 으로 겨룬다 · 지면 409")
    void rotate() {
        String oldHash = OperatorInvitationTokens.sha256Hex("old-token");
        given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(actorRow()));
        given(invitationPort.rotateIfPending(eq(7L), eq(3), anyString(), any(), eq(11L), any())).willReturn(true);
        given(auditor.newAuditId()).willReturn("audit-r");
        given(invitationPort.findByInvitationId(INV)).willReturn(Optional.of(view("PENDING", 4, "new")));

        OperatorInvitationWriter.Issued issued = writer.rotate(ACTOR, view("PENDING", 3, oldHash), "r");

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(invitationPort).rotateIfPending(eq(7L), eq(3), newHash.capture(), any(), eq(11L), any());
        assertThat(newHash.getValue()).isEqualTo(OperatorInvitationTokens.sha256Hex(issued.token()))
                .isNotEqualTo(oldHash);

        given(invitationPort.rotateIfPending(anyLong(), anyInt(), anyString(), any(), anyLong(), any())).willReturn(false);
        assertThatThrownBy(() -> writer.rotate(ACTOR, view("PENDING", 3, oldHash), "r"))
                .isInstanceOf(OperatorInvitationNotPendingException.class);
    }
}
