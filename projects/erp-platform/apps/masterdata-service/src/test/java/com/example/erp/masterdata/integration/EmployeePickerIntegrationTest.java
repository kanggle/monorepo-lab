package com.example.erp.masterdata.integration;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.CreateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.CreateJobGradeCommand;
import com.example.erp.masterdata.application.command.Commands.RetireEmployeeCommand;
import com.example.erp.masterdata.application.view.EmployeePickerResult;
import com.example.erp.masterdata.application.view.EmployeePickerView;
import com.example.erp.masterdata.domain.authorization.ListDataScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-ERP-BE-045 — the picker's subtree scope on real MySQL (H2 forbidden): the one-query department-edge
 * expansion ({@code DepartmentRepositoryImpl.subtreeIds}) and the {@code department_id IN (…)} page query
 * ({@code EmployeeRepositoryImpl.findPickerPage}), ordered by {@code employee_number}.
 *
 * <pre>
 *   root ─ child ─ grandchild        sibling (separate tree)
 *   e-root  e-child  e-grand          e-sibling
 * </pre>
 *
 * The MySQL container is shared JVM-wide and other suites write tenant {@code erp} too, so tenant-wide
 * assertions use {@code contains}, and every code carries a random suffix.
 */
class EmployeePickerIntegrationTest extends AbstractMasterdataIntegrationTest {

    private static final ActorContext FIXTURE = new ActorContext("fixture", TENANT_ERP,
            Set.of("erp.write", "erp.read"), Set.of("*"));
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    @Autowired
    MasterdataApplicationService service;

    private String sfx;
    private String root;
    private String child;
    private String sibling;
    private String eRoot;
    private String eChild;
    private String eGrand;
    private String eSibling;

    @BeforeEach
    void tree() {
        sfx = UUID.randomUUID().toString().substring(0, 8);
        root = dept("ROOT", null);
        child = dept("CHILD", root);
        String grandchild = dept("GRAND", child);
        sibling = dept("SIB", null);
        String cc = service.createCostCenter(new CreateCostCenterCommand(FIXTURE,
                "BE045-CC-" + sfx, "CC", root, FROM)).id();
        String jg = service.createJobGrade(new CreateJobGradeCommand(FIXTURE,
                "BE045-JG-" + sfx, "JG", 1, FROM)).id();
        // Numbers chosen so the expected order differs from creation order (sort is by employeeNumber).
        eGrand = emp("1", grandchild, cc, jg);
        eSibling = emp("2", sibling, cc, jg);
        eChild = emp("3", child, cc, jg);
        eRoot = emp("4", root, cc, jg);
    }

    private String dept(String label, String parentId) {
        return service.createDepartment(new CreateDepartmentCommand(FIXTURE,
                "BE045-" + label + "-" + sfx, label + " " + sfx, parentId, FROM)).id();
    }

    private String emp(String n, String deptId, String cc, String jg) {
        return service.createEmployee(new CreateEmployeeCommand(FIXTURE,
                "BE045-" + sfx + "-" + n, "Emp " + n, deptId, cc, jg, FROM)).id();
    }

    private static ActorContext approver(Set<String> orgScope) {
        return new ActorContext("approver", TENANT_ERP, Set.of("ERP_OPERATOR", "ERP_APPROVAL_WRITE"), orgScope);
    }

    @Test
    @DisplayName("🔴 BE-045: org_scope=[root] → root + descendants only, sibling tree excluded, sorted by employeeNumber")
    void rootScope() {
        EmployeePickerResult r = service.listEmployeePicker(approver(Set.of(root)), null, 0, 500);

        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.SUBTREE);
        assertThat(r.page().content()).extracting(EmployeePickerView::id).containsExactly(eGrand, eChild, eRoot);
        assertThat(r.page().totalElements()).isEqualTo(3L);
        assertThat(r.page().content().get(0).departmentName()).isEqualTo("GRAND " + sfx);
        assertThat(r.page().content()).allMatch(v -> !v.accountLinked());
    }

    @Test
    @DisplayName("BE-045: org_scope=[child] → child subtree only (does not climb to root)")
    void innerRootScope() {
        EmployeePickerResult r = service.listEmployeePicker(approver(Set.of(child)), null, 0, 500);
        assertThat(r.page().content()).extracting(EmployeePickerView::id).containsExactly(eGrand, eChild);
    }

    @Test
    @DisplayName("BE-045: org_scope='*' → whole tenant (contains all four), scope TENANT")
    void tenantScope() {
        EmployeePickerResult r = service.listEmployeePicker(approver(Set.of("*")), null, 0, 500);
        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.TENANT);
        assertThat(r.page().content()).extracting(EmployeePickerView::id)
                .contains(eGrand, eSibling, eChild, eRoot);
    }

    @Test
    @DisplayName("BE-045: empty org_scope → empty page, scope SUBTREE")
    void emptyScope() {
        EmployeePickerResult r = service.listEmployeePicker(approver(Set.of()), null, 0, 500);
        assertThat(r.page().content()).isEmpty();
        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.SUBTREE);
    }

    @Test
    @DisplayName("BE-045: active=true drops a RETIRED employee; omitted keeps it (with status RETIRED)")
    void activeFilter() {
        service.retireEmployee(new RetireEmployeeCommand(FIXTURE, eChild, "left"));

        List<EmployeePickerView> active = service.listEmployeePicker(approver(Set.of(root)), Boolean.TRUE, 0, 500)
                .page().content();
        assertThat(active).extracting(EmployeePickerView::id).containsExactly(eGrand, eRoot);

        List<EmployeePickerView> all = service.listEmployeePicker(approver(Set.of(root)), null, 0, 500)
                .page().content();
        assertThat(all).extracting(EmployeePickerView::id).containsExactly(eGrand, eChild, eRoot);
        assertThat(all.get(1).status()).isEqualTo("RETIRED");
    }

    @Test
    @DisplayName("BE-045: paging over the scoped query — totalElements is the true scoped count")
    void paging() {
        EmployeePickerResult p0 = service.listEmployeePicker(approver(Set.of(root)), null, 0, 2);
        assertThat(p0.page().content()).extracting(EmployeePickerView::id).containsExactly(eGrand, eChild);
        assertThat(p0.page().totalElements()).isEqualTo(3L);
        assertThat(p0.page().totalPages()).isEqualTo(2);
        EmployeePickerResult p1 = service.listEmployeePicker(approver(Set.of(root)), null, 1, 2);
        assertThat(p1.page().content()).extracting(EmployeePickerView::id).containsExactly(eRoot);
    }
}
