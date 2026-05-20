package com.example.erp.masterdata.domain.department;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataParentCycleException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataReferenceViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepartmentTest {

    private static final Instant NOW = Instant.parse("2026-05-20T00:00:00Z");

    @Test
    @DisplayName("create sets ACTIVE + open-ended period")
    void createDefaults() {
        Department d = Department.create("d-1", "erp", "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        assertThat(d.getStatus()).isEqualTo(MasterStatus.ACTIVE);
        assertThat(d.getEffectiveTo()).isNull();
        assertThat(d.getParentId()).isNull();
        assertThat(d.isActive()).isTrue();
    }

    @Test
    @DisplayName("E1: self-parent is a parent-cycle")
    void selfParentRejected() {
        Department d = Department.create("d-1", "erp", "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        assertThatThrownBy(() -> d.updateParent("d-1", NOW))
                .isInstanceOf(MasterdataParentCycleException.class);
    }

    @Test
    @DisplayName("retire: ACTIVE → RETIRED + effective_to closed")
    void retireSetsEnd() {
        Department d = Department.create("d-1", "erp", "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        d.retire(NOW);
        assertThat(d.getStatus()).isEqualTo(MasterStatus.RETIRED);
        assertThat(d.getEffectiveTo()).isNotNull();
    }

    @Test
    @DisplayName("E1: double retire is rejected")
    void doubleRetireRejected() {
        Department d = Department.create("d-1", "erp", "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        d.retire(NOW);
        assertThatThrownBy(() -> d.retire(NOW))
                .isInstanceOf(MasterdataReferenceViolationException.class);
    }
}
