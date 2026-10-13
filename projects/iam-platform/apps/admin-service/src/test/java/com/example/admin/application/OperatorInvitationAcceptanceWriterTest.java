package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * TASK-MONO-772 S3 — the acceptance transaction (auth-to-admin.md § accept, «8 의 트랜잭션이 쓰는 것»): claim first,
 * then the operator with {@code oidc_subject = accountId}, grants confined to the invitation's tenant, the
 * assignment, {@code accepted_operator_id}, and the self-flow audit row. A lost claim writes nothing else.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorInvitationAcceptanceWriter — 수락 트랜잭션 (TASK-MONO-772 S3)")
class OperatorInvitationAcceptanceWriterTest {

    private static final String TENANT = "acme-corp";
    private static final String ACCOUNT = "0199de70-0000-7000-8000-0000000acc01";

    @Mock OperatorInvitationPort invitationPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock AdminActionAuditor auditor;
    OperatorInvitationAcceptanceWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OperatorInvitationAcceptanceWriter(invitationPort, operatorPort, assignmentPort, auditor,
                new OperatorMembershipAddition(operatorPort, assignmentPort, null));
    }

    private static InvitationView pending() {
        return new InvitationView(7L, "inv-1", TENANT, "person@example.com", "홍길동",
                List.of("SUPPORT_LOCK", "TENANT_ADMIN"), "PENDING", Instant.now().plus(Duration.ofDays(1)),
                11L, "op-admin-x", "SENT", Instant.EPOCH, null, null, null, Instant.EPOCH, 0, "h".repeat(64));
    }

    private static List<AdminOperatorPort.RoleView> roles() {
        return List.of(new AdminOperatorPort.RoleView(3L, "SUPPORT_LOCK", "", false),
                new AdminOperatorPort.RoleView(4L, "TENANT_ADMIN", "", false));
    }

    @Test
    @DisplayName("성공: 선점 → 운영자(홈=초대 테넌트 · 비밀번호 없음 · ACTIVE) → oidc_subject=계정 → 역할(테넌트=초대 테넌트) → 배정 → accepted_operator_id → 감사(<self_invitation_accept>)")
    void writesEverythingInOrder() {
        given(invitationPort.acceptIfPending(eq(7L), eq("h".repeat(64)), eq(ACCOUNT), any())).willReturn(true);
        given(operatorPort.createOperator(any())).willAnswer(inv -> {
            AdminOperatorPort.NewOperator row = inv.getArgument(0);
            return new AdminOperatorPort.OperatorView(99L, row.operatorId(), row.tenantId(), row.email(),
                    row.passwordHash(), row.displayName(), row.status(), null, null, row.createdAt(),
                    row.createdAt(), null, null);
        });
        given(auditor.newAuditId()).willReturn("audit-1");

        OperatorInvitationAcceptanceWriter.Accepted accepted = writer.accept(pending(), ACCOUNT, roles(), 11L);

        assertThat(accepted.operatorInternalId()).isEqualTo(99L);
        assertThat(accepted.auditId()).isEqualTo("audit-1");

        InOrder order = inOrder(invitationPort, operatorPort, assignmentPort, auditor);
        order.verify(invitationPort).acceptIfPending(eq(7L), eq("h".repeat(64)), eq(ACCOUNT), any());
        ArgumentCaptor<AdminOperatorPort.NewOperator> row = ArgumentCaptor.forClass(AdminOperatorPort.NewOperator.class);
        order.verify(operatorPort).createOperator(row.capture());
        order.verify(operatorPort).updateOidcSubject(eq(99L), eq(ACCOUNT), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminOperatorPort.NewRoleBinding>> grants = ArgumentCaptor.forClass(List.class);
        order.verify(operatorPort).saveOperatorRoles(grants.capture());
        order.verify(assignmentPort).createAssignment(99L, TENANT, 11L);
        order.verify(invitationPort).recordAcceptedOperator(eq(7L), eq(99L), any());
        ArgumentCaptor<AdminActionAuditor.AuditRecord> audit = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        order.verify(auditor).recordWithPermission(audit.capture(), eq("<self_invitation_accept>"));

        assertThat(row.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(row.getValue().email()).isEqualTo("person@example.com");
        assertThat(row.getValue().displayName()).isEqualTo("홍길동");
        assertThat(row.getValue().passwordHash()).as("OIDC only — no break-glass password").isNull();
        assertThat(row.getValue().status()).isEqualTo("ACTIVE");
        assertThat(row.getValue().operatorId()).isEqualTo(accepted.operatorId());

        assertThat(grants.getValue()).hasSize(2).allSatisfy(g -> {
            assertThat(g.operatorInternalId()).isEqualTo(99L);
            assertThat(g.tenantId()).as("D2 — the invitation's tenant, never '*'").isEqualTo(TENANT);
            assertThat(g.grantedBy()).isEqualTo(11L);
        });
        assertThat(grants.getValue()).extracting(AdminOperatorPort.NewRoleBinding::roleId).containsExactly(3L, 4L);

        AdminActionAuditor.AuditRecord a = audit.getValue();
        assertThat(a.actionCode()).isEqualTo(ActionCode.OPERATOR_INVITATION_ACCEPT);
        assertThat(a.operator().operatorId()).as("the actor is the operator just created").isEqualTo(accepted.operatorId());
        assertThat(a.reason()).isEqualTo("<self_invitation_accept>");
        assertThat(a.targetType()).isEqualTo("OPERATOR_INVITATION");
        assertThat(a.targetId()).isEqualTo("inv-1");
        assertThat(a.targetTenantId()).isEqualTo(TENANT);
        assertThat(a.downstreamDetail()).isEqualTo("accountId=" + ACCOUNT);
        assertThat(a.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(String.valueOf(a)).doesNotContain("person@example.com").doesNotContain("h".repeat(64));
    }

    @Test
    @DisplayName("선점 실패(0행) → ClaimLost · 운영자 · 역할 · 배정 · 감사 아무것도 안 쓴다")
    void lostClaim_writesNothingElse() {
        given(invitationPort.acceptIfPending(anyLong(), any(), any(), any())).willReturn(false);

        assertThatThrownBy(() -> writer.accept(pending(), ACCOUNT, roles(), 11L))
                .isInstanceOf(OperatorInvitationAcceptanceWriter.ClaimLost.class);

        verify(invitationPort).acceptIfPending(anyLong(), any(), any(), any());
        verifyNoMoreInteractions(invitationPort);
        verifyNoInteractions(operatorPort, assignmentPort, auditor);
    }

    /**
     * 🔴 TASK-MONO-784 S5 (auth-to-admin.md § accept «소속 추가일 때» · AC-3) — real-writing in-memory ports: the
     * fake operator port throws on every operator-row write (create · oidc_subject · identity link), and the
     * other companies' role rows are compared byte for byte.
     */
    @Test
    @DisplayName("🔴 784 S5 소속 추가: 선점 → 기존 행에 초대 테넌트 역할 행(직접) + 배정 → accepted_operator_id = 기존 행 → 감사(행위자 = 기존 운영자 · membershipAdded=true) · 운영자 행 쓰기 0 · 다른 회사 행 바이트 불변")
    void acceptAsMembership_writesOnlyTheInvitationTenant() {
        InMemoryRoleRowsPort operators = new InMemoryRoleRowsPort()
                .role(3, "SUPPORT_LOCK").role(4, "TENANT_ADMIN").role(5, "TENANT_BILLING_ADMIN");
        AdminOperatorPort.OperatorView existing = new AdminOperatorPort.OperatorView(41L, "op-41", "home-co",
                "person@example.com", null, "기존 이름", "ACTIVE", null, null, Instant.EPOCH, Instant.EPOCH, null,
                "identity-41");
        operators.operator(existing)
                .row(41L, "TENANT_ADMIN", "home-co", null, null)
                .row(41L, "TENANT_BILLING_ADMIN", "home-co", null, null)
                .row(41L, "SUPPORT_LOCK", "other-co", 8L, null);
        InMemoryAssignmentPort assignments = new InMemoryAssignmentPort().allowCreate()
                .row(41L, "home-co", null, null).row(41L, "other-co", 8L, null);
        List<InMemoryRoleRowsPort.Row> rolesBefore = List.copyOf(operators.rows);
        List<InMemoryAssignmentPort.Row> assignmentsBefore = List.copyOf(assignments.rows);
        OperatorInvitationAcceptanceWriter membershipWriter = new OperatorInvitationAcceptanceWriter(invitationPort,
                operators, assignments, auditor, new OperatorMembershipAddition(operators, assignments, null));
        given(invitationPort.acceptIfPending(eq(7L), eq("h".repeat(64)), eq(ACCOUNT), any())).willReturn(true);
        given(auditor.newAuditId()).willReturn("audit-2");

        OperatorInvitationAcceptanceWriter.Accepted accepted =
                membershipWriter.acceptAsMembership(pending(), ACCOUNT, existing, roles(), 11L);

        assertThat(accepted.operatorId()).isEqualTo("op-41");
        assertThat(accepted.operatorInternalId()).isEqualTo(41L);
        // the invitation's tenant: exactly the invited roles, direct
        assertThat(operators.rowsIn(41L, TENANT))
                .extracting(InMemoryRoleRowsPort.Row::roleName, InMemoryRoleRowsPort.Row::groupOrigin,
                        InMemoryRoleRowsPort.Row::orgNodeId)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("SUPPORT_LOCK", null, null),
                        org.assertj.core.groups.Tuple.tuple("TENANT_ADMIN", null, null));
        // 🔴 home-co · other-co rows byte-unchanged
        assertThat(operators.rowsOutside(41L, TENANT)).containsExactlyElementsOf(rolesBefore);
        assertThat(assignments.rows).containsAll(assignmentsBefore).hasSize(assignmentsBefore.size() + 1);
        assertThat(assignments.find(41L, TENANT)).hasValueSatisfying(r -> assertThat(r.grantedBy()).isEqualTo(11L));
        // the operator row: home and display name as they were (the fake would have thrown on any write)
        assertThat(operators.findByOperatorId("op-41").orElseThrow().tenantId()).isEqualTo("home-co");
        assertThat(operators.findByOperatorId("op-41").orElseThrow().displayName()).isEqualTo("기존 이름");

        verify(invitationPort).recordAcceptedOperator(eq(7L), eq(41L), any());
        ArgumentCaptor<AdminActionAuditor.AuditRecord> audit = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(audit.capture(), eq("<self_invitation_accept>"));
        assertThat(audit.getValue().operator().operatorId()).isEqualTo("op-41");
        assertThat(audit.getValue().targetTenantId()).isEqualTo(TENANT);
        assertThat(audit.getValue().downstreamDetail()).isEqualTo("accountId=" + ACCOUNT + ",membershipAdded=true");
    }

    @Test
    @DisplayName("784 S5 소속 추가 · 선점 실패 → ClaimLost · 역할 · 배정 · 감사 0")
    void acceptAsMembership_lostClaim_writesNothingElse() {
        given(invitationPort.acceptIfPending(anyLong(), any(), any(), any())).willReturn(false);
        AdminOperatorPort.OperatorView existing = new AdminOperatorPort.OperatorView(41L, "op-41", "home-co",
                "person@example.com", null, "D", "ACTIVE", null, null, Instant.EPOCH, Instant.EPOCH, null, null);

        assertThatThrownBy(() -> writer.acceptAsMembership(pending(), ACCOUNT, existing, roles(), 11L))
                .isInstanceOf(OperatorInvitationAcceptanceWriter.ClaimLost.class);

        verifyNoInteractions(operatorPort, assignmentPort, auditor);
    }
}
