package com.example.erp.masterdata.integration;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.example.erp.masterdata.domain.error.DomainErrors.DataScopeForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Subtree-containment data-scope IT (TASK-ERP-BE-008 / ADR-MONO-020 D3
 * amendment). Builds a real department tree in MySQL (H2 forbidden) and proves
 * that an operator whose {@code org_scope} = a subtree-ROOT can write anywhere
 * within that root's subtree (descendants resolved via the real
 * {@code department} hierarchy) and is fail-CLOSED outside it — while the
 * platform-scope (`"*"`) and unscoped paths behave exactly as before (net-zero).
 *
 * <p>Drives the application layer directly (same shape as the lifecycle IT) so
 * the data-scope target derivation (create → {@code parentId}) is exercised
 * end-to-end through {@code RoleScopeAuthorizationAdapter} +
 * {@code DepartmentRepository.ancestors}.
 */
class DataScopeSubtreeIntegrationTest extends AbstractMasterdataIntegrationTest {

    /** Platform-scope seeder — used only to build the tree (net-zero "*"). */
    private static final ActorContext PLATFORM = new ActorContext("seed-op", TENANT_ERP,
            Set.of("erp.write", "erp.read"), Set.of("*"));

    @Autowired
    MasterdataApplicationService service;

    @Test
    @DisplayName("AC-1/AC-5: operator org_scope=[sales-root] writes within the sales subtree (200) and is denied outside (DATA_SCOPE_FORBIDDEN)")
    void subtreeContainmentWriteIsolation() {
        // Build a tree as platform-scope:  salesRoot ─ salesEast ─ salesEast1 ;  engRoot ─ engChild
        DepartmentView salesRoot = create(PLATFORM, "DSS-SALES-ROOT", null);
        DepartmentView salesEast = create(PLATFORM, "DSS-SALES-EAST", salesRoot.id());
        DepartmentView engRoot = create(PLATFORM, "DSS-ENG-ROOT", null);

        // Operator scoped to the SALES subtree-root only.
        ActorContext salesOperator = new ActorContext("sales-op", TENANT_ERP,
                Set.of("erp.write"), Set.of(salesRoot.id()));

        // WRITE within the sales subtree: a child whose parent is salesEast
        // (descendant of salesRoot) → target=salesEast.id() resolves in-scope.
        DepartmentView salesEast1 = create(salesOperator, "DSS-SALES-EAST-1", salesEast.id());
        assertThat(salesEast1.status()).isEqualTo("ACTIVE");

        // A direct child of the scoped root itself → target=salesRoot.id() (in-scope).
        DepartmentView salesWest = create(salesOperator, "DSS-SALES-WEST", salesRoot.id());
        assertThat(salesWest.status()).isEqualTo("ACTIVE");

        // WRITE outside the subtree: child of engRoot → target=engRoot.id() (out-of-scope) → 403.
        assertThatThrownBy(() -> create(salesOperator, "DSS-ENG-CHILD", engRoot.id()))
                .isInstanceOf(DataScopeForbiddenException.class);
    }

    @Test
    @DisplayName("AC-2 net-zero: org_scope=[\"*\"] writes to any subtree (200) — platform bypass unchanged")
    void platformScopeBypassesSubtreeCheck() {
        DepartmentView anyRoot = create(PLATFORM, "DSS-NZ-ROOT", null);
        DepartmentView child = create(PLATFORM, "DSS-NZ-CHILD", anyRoot.id());
        assertThat(child.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("AC-2 fail-closed: empty org_scope ([]) + targeted write → DATA_SCOPE_FORBIDDEN")
    void emptyScopeTargetedWriteDenied() {
        DepartmentView root = create(PLATFORM, "DSS-FC-ROOT", null);
        ActorContext zeroScope = new ActorContext("zero-op", TENANT_ERP,
                Set.of("erp.write"), Set.of());
        assertThatThrownBy(() -> create(zeroScope, "DSS-FC-CHILD", root.id()))
                .isInstanceOf(DataScopeForbiddenException.class);
    }

    private DepartmentView create(ActorContext actor, String code, String parentId) {
        return service.createDepartment(new CreateDepartmentCommand(
                actor, code, code, parentId, LocalDate.of(2026, 1, 1)));
    }
}
