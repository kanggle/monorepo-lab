package com.example.erp.masterdata.domain.employee;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link Employee} account-link invariants (TASK-ERP-BE-044). Pure — no Spring. */
class EmployeeAccountLinkTest {

    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    private static Employee employee() {
        return Employee.create("emp-1", "erp", "E-1", "Hong", "dept-1", "cc-1", "jg-1",
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
    }

    @Test
    @DisplayName("a new employee is unlinked")
    void newEmployeeUnlinked() {
        assertThat(employee().getAccountId()).isNull();
        assertThat(employee().isLinked()).isFalse();
    }

    @Test
    @DisplayName("link → accountId set; a second link → CONFLICT employee_already_linked")
    void linkOnce() {
        Employee e = employee();
        e.linkAccount("acct-A", NOW);
        assertThat(e.getAccountId()).isEqualTo("acct-A");

        assertThatThrownBy(() -> e.linkAccount("acct-B", NOW))
                .isInstanceOfSatisfying(EmployeeLinkConflictException.class, ex ->
                        assertThat(ex.conflictCause()).isEqualTo("employee_already_linked"));
        assertThat(e.getAccountId()).isEqualTo("acct-A");
    }

    @Test
    @DisplayName("AC-5: a RETIRED employee cannot gain a link → INVALID employee_not_active")
    void retiredCannotLink() {
        Employee e = employee();
        e.retire(NOW);

        assertThatThrownBy(() -> e.linkAccount("acct-A", NOW))
                .isInstanceOfSatisfying(EmployeeLinkInvalidException.class, ex ->
                        assertThat(ex.details()).containsEntry("cause", "employee_not_active"));
        assertThat(e.getAccountId()).isNull();
    }

    @Test
    @DisplayName("AC-5 Edge Case: retiring a linked employee KEEPS the link")
    void retireKeepsLink() {
        Employee e = employee();
        e.linkAccount("acct-A", NOW);

        e.retire(NOW);

        assertThat(e.getStatus()).isEqualTo(MasterStatus.RETIRED);
        assertThat(e.getAccountId()).isEqualTo("acct-A");
    }

    @Test
    @DisplayName("unlink clears the link; unlinking an unlinked employee → CONFLICT not_linked")
    void unlink() {
        Employee e = employee();
        assertThatThrownBy(() -> e.unlinkAccount(NOW))
                .isInstanceOfSatisfying(EmployeeLinkConflictException.class, ex ->
                        assertThat(ex.conflictCause()).isEqualTo("not_linked"));

        e.linkAccount("acct-A", NOW);
        e.unlinkAccount(NOW);
        assertThat(e.getAccountId()).isNull();
    }
}
