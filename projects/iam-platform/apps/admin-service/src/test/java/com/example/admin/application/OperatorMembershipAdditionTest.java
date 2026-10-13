package com.example.admin.application;

import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-784 S5 (ADR-MONO-082 b · D2 · D4 · D-12) — the shared «add a membership to an existing row».
 *
 * <p>Real-writing in-memory ports ({@link InMemoryRoleRowsPort} · {@link InMemoryAssignmentPort}), not mocks: the
 * 🔴 AC-3 cell is «the operator's other companies' rows are BYTE-UNCHANGED», and a mock lets a wrong write
 * (a row stamped with the home, a write into every member tenant) pass silently. The fake operator port also
 * throws on every operator-row write (create · oidc_subject · identity link …), so «no operator-row write» is
 * asserted by construction.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorMembershipAddition — 기존 행에 소속 추가 (TASK-MONO-784 S5 · D-12)")
class OperatorMembershipAdditionTest {

    private static final long OP = 41L;
    private static final long OTHER_OP = 42L;
    private static final String HOME = "alpha";
    private static final String BETA = "beta";
    private static final String NEW = "gamma";

    @Mock CachingPermissionEvaluator cache;

    private InMemoryRoleRowsPort operators;
    private InMemoryAssignmentPort assignments;
    private OperatorMembershipAddition addition;

    private static AdminOperatorPort.OperatorView op(long id, String uuid, String home, String status) {
        return new AdminOperatorPort.OperatorView(id, uuid, home, uuid + "@example.com", null, "D", status,
                null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }

    @BeforeEach
    void setUp() {
        operators = new InMemoryRoleRowsPort()
                .role(1, "TENANT_ADMIN").role(2, "TENANT_BILLING_ADMIN").role(3, "SUPPORT_LOCK")
                .role(9, "SUPER_ADMIN")
                .operator(op(OP, "op-41", HOME, "ACTIVE"))
                .operator(op(OTHER_OP, "op-42", NEW, "ACTIVE"))
                // the person's two existing companies: alpha (direct · group · node) and beta (direct)
                .row(OP, "TENANT_ADMIN", HOME, null, null)
                .row(OP, "SUPPORT_LOCK", HOME, 7L, null)
                .row(OP, "TENANT_BILLING_ADMIN", HOME, null, "node-1")
                .row(OP, "SUPPORT_LOCK", BETA, null, null)
                // someone else, already in the target company
                .row(OTHER_OP, "TENANT_ADMIN", NEW, null, null);
        assignments = new InMemoryAssignmentPort()
                .row(OP, HOME, null, null)
                .row(OP, BETA, null, List.of("dept-1"))
                .row(OTHER_OP, NEW, null, null)
                .allowCreate();
        addition = new OperatorMembershipAddition(operators, assignments, cache);
    }

    private List<InMemoryRoleRowsPort.Row> snapshotOutside(String tenant) {
        List<InMemoryRoleRowsPort.Row> out = new ArrayList<>(operators.rowsOutside(OP, tenant));
        out.addAll(operators.rows.stream().filter(r -> r.operatorId() != OP).toList());
        return out;
    }

    @Test
    @DisplayName("🔴 AC-3: 새 테넌트의 역할 행 + 배정만 생긴다 · 기존 회사(alpha 직접·그룹·노드 · beta) 역할 행과 배정은 바이트 불변 · 다른 사람 행 무접촉")
    void addsOnlyTheNewTenantRows_existingCompaniesByteUnchanged() {
        List<InMemoryRoleRowsPort.Row> rolesBefore = snapshotOutside(NEW);
        List<InMemoryAssignmentPort.Row> assignmentsBefore = new ArrayList<>(assignments.rows);
        AdminOperatorPort.OperatorView facet = operators.findByOperatorId("op-41").orElseThrow();

        addition.requireCanReceive(facet, NEW);
        addition.write(OP, NEW, operators.resolveRolesByName(List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN")).values(),
                null, Instant.parse("2026-10-13T00:00:00Z"));

        // the new company: exactly the two roles, direct, stamped with the NEW tenant (not the home)
        assertThat(operators.rowsIn(OP, NEW))
                .extracting(InMemoryRoleRowsPort.Row::roleName, InMemoryRoleRowsPort.Row::tenantId,
                        InMemoryRoleRowsPort.Row::groupOrigin, InMemoryRoleRowsPort.Row::orgNodeId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("TENANT_ADMIN", NEW, null, null),
                        org.assertj.core.groups.Tuple.tuple("TENANT_BILLING_ADMIN", NEW, null, null));
        // 🔴 every other row — this person's alpha · beta rows (granted_at included) and the other operator's
        assertThat(snapshotOutside(NEW)).containsExactlyElementsOf(rolesBefore);
        // assignments: the old ones byte-unchanged (org_scope included), one new whole-tenant row for gamma
        assertThat(assignments.rows).containsAll(assignmentsBefore).hasSize(assignmentsBefore.size() + 1);
        assertThat(assignments.find(OP, NEW)).hasValueSatisfying(r -> {
            assertThat(r.orgScope()).isNull();
            assertThat(r.groupOrigin()).isNull();
        });
        // the operator row itself (home) — the fake throws on any operator-row write; read it back anyway
        assertThat(operators.findByOperatorId("op-41").orElseThrow().tenantId()).isEqualTo(HOME);
    }

    @Test
    @DisplayName("🔴 같은 테넌트 재소속(이미 배정 있음) → 409 OPERATOR_ALREADY_PROVISIONED · 쓰기 0")
    void alreadyMember_409() {
        int before = operators.rows.size();
        AdminOperatorPort.OperatorView facet = operators.findByOperatorId("op-41").orElseThrow();

        assertThatThrownBy(() -> addition.requireCanReceive(facet, BETA))
                .isInstanceOf(OperatorAlreadyProvisionedException.class)
                .extracting(e -> ((OperatorAlreadyProvisionedException) e).getHttpStatus()).isEqualTo(409);
        assertThat(operators.rows).hasSize(before);
    }

    @Test
    @DisplayName("🔴 D2: '*' 행(홈 '*' 또는 '*' 역할 행) → 409 · 대조군 같은 사람이 회사 행이면 통과")
    void platformRow_409_byHomeOrByRoleRow() {
        operators.operator(op(50L, "op-star-home", "*", "ACTIVE"));
        assertThatThrownBy(() -> addition.requireCanReceive(operators.findByOperatorId("op-star-home").orElseThrow(), NEW))
                .isInstanceOf(OperatorAlreadyProvisionedException.class);

        // home is a company, but the row holds a '*' role row — still a platform row
        operators.operator(op(51L, "op-star-row", HOME, "ACTIVE")).row(51L, "SUPER_ADMIN", "*", null, null);
        assertThatThrownBy(() -> addition.requireCanReceive(operators.findByOperatorId("op-star-row").orElseThrow(), NEW))
                .isInstanceOf(OperatorAlreadyProvisionedException.class);

        // control: a company row, ACTIVE, not a member of gamma → passes
        addition.requireCanReceive(operators.findByOperatorId("op-41").orElseThrow(), NEW);
    }

    @Test
    @DisplayName("비-ACTIVE 행 → 409 (정지된 사람이 소속 추가로 측면을 되살리지 않는다)")
    void suspended_409() {
        operators.operator(op(52L, "op-susp", HOME, "SUSPENDED"));
        assertThatThrownBy(() -> addition.requireCanReceive(operators.findByOperatorId("op-susp").orElseThrow(), NEW))
                .isInstanceOf(OperatorAlreadyProvisionedException.class);
    }

    @Test
    @DisplayName("🔴 D2 · D-14: 회사 행에 '*' 소속 · '*' 역할 행 → 400(IllegalArgumentException) · 쓰기 0")
    void starTenant_refusedForACompanyRow() {
        int before = operators.rows.size();
        AdminOperatorPort.OperatorView facet = operators.findByOperatorId("op-41").orElseThrow();

        assertThatThrownBy(() -> addition.requireCanReceive(facet, "*")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> addition.write(OP, "*",
                operators.resolveRolesByName(List.of("SUPER_ADMIN")).values(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(operators.rows).hasSize(before);
        assertThat(assignments.find(OP, "*")).isEmpty();
    }

    @Test
    @DisplayName("D-5: 캐시 무효화는 best-effort — 실패해도 던지지 않는다")
    void cacheInvalidation_bestEffort() {
        addition.invalidatePermissionCache("op-41");
        verify(cache).invalidate("op-41");

        doThrow(new IllegalStateException("redis down")).when(cache).invalidate("op-42");
        addition.invalidatePermissionCache("op-42");
    }
}
