package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.command.MasterChangeCommand;
import com.example.erp.readmodel.domain.common.ChangeKind;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.repository.CostCenterProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.JobGradeProjectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApplyMasterChangeUseCaseTest {

    @Mock DepartmentProjectionRepository departmentRepository;
    @Mock EmployeeProjectionRepository employeeRepository;
    @Mock JobGradeProjectionRepository jobGradeRepository;
    @Mock CostCenterProjectionRepository costCenterRepository;
    @Mock EventDedupeService dedupeService;

    @InjectMocks ApplyMasterChangeUseCase useCase;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private MasterChangeCommand departmentCreate() {
        return new MasterChangeCommand("evt-1", "erp.masterdata.department.changed.v1",
                "dept-1", ChangeKind.CREATED, T0,
                Map.of("code", "SALES", "name", "영업본부", "parentId", "hq", "status", "ACTIVE"));
    }

    @Test
    void departmentCreatedInsertsNewProjectionAndMarksProcessed() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.empty());

        useCase.applyDepartment(departmentCreate());

        ArgumentCaptor<DepartmentProjection> captor =
                ArgumentCaptor.forClass(DepartmentProjection.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().code()).isEqualTo("SALES");
        assertThat(captor.getValue().parentId()).isEqualTo("hq");
        verify(dedupeService).markProcessed("evt-1",
                "erp.masterdata.department.changed.v1", "dept-1");
    }

    @Test
    void duplicateEventIsSkippedWithoutMutation() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(true);

        useCase.applyDepartment(departmentCreate());

        verify(departmentRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(dedupeService, never()).markProcessed(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void departmentParentMovedUpsertsExistingWithNewParent() {
        DepartmentProjection existing = DepartmentProjection.of(
                "dept-1", "SALES", "영업", "old-parent", MasterStatus.ACTIVE,
                null, null, T0, "evt-0");
        when(dedupeService.isDuplicate("evt-2")).thenReturn(false);
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(existing));

        MasterChangeCommand cmd = new MasterChangeCommand("evt-2",
                "erp.masterdata.department.changed.v1", "dept-1", ChangeKind.PARENT_MOVED, T0,
                Map.of("code", "SALES", "name", "영업본부", "parentId", "new-parent", "status", "ACTIVE"));
        useCase.applyDepartment(cmd);

        assertThat(existing.parentId()).isEqualTo("new-parent");
        verify(departmentRepository).save(existing);
        verify(dedupeService).markProcessed("evt-2",
                "erp.masterdata.department.changed.v1", "dept-1");
    }

    @Test
    void employeeRetiredMarksStatusRetainingRow() {
        EmployeeProjection existing = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "dept-1", "cc-1", "jg-1",
                MasterStatus.ACTIVE, null, null, T0, "evt-0");
        when(dedupeService.isDuplicate("evt-r")).thenReturn(false);
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(existing));

        MasterChangeCommand cmd = new MasterChangeCommand("evt-r",
                "erp.masterdata.employee.changed.v1", "emp-1", ChangeKind.RETIRED, T0,
                Map.of("effectiveTo", "2026-03-01"));
        useCase.applyEmployee(cmd);

        assertThat(existing.status()).isEqualTo(MasterStatus.RETIRED);
        verify(employeeRepository).save(existing);
        verify(dedupeService).markProcessed("evt-r",
                "erp.masterdata.employee.changed.v1", "emp-1");
    }

    @Test
    void jobGradeCreatedInsertsWithDisplayOrder() {
        when(dedupeService.isDuplicate("evt-jg")).thenReturn(false);
        when(jobGradeRepository.findById("jg-1")).thenReturn(Optional.empty());

        MasterChangeCommand cmd = new MasterChangeCommand("evt-jg",
                "erp.masterdata.jobgrade.changed.v1", "jg-1", ChangeKind.CREATED, T0,
                Map.of("code", "G3", "name", "사원", "displayOrder", 30, "status", "ACTIVE"));
        useCase.applyJobGrade(cmd);

        verify(jobGradeRepository).save(org.mockito.ArgumentMatchers.argThat(
                p -> p.displayOrder() == 30 && "G3".equals(p.code())));
        verify(dedupeService).markProcessed("evt-jg",
                "erp.masterdata.jobgrade.changed.v1", "jg-1");
    }

    @Test
    void costCenterUpdatedUpsertsExisting() {
        com.example.erp.readmodel.domain.projection.CostCenterProjection existing =
                com.example.erp.readmodel.domain.projection.CostCenterProjection.of(
                        "cc-1", "CC-100", "old", "dept-1", MasterStatus.ACTIVE, null, null, T0, "evt-0");
        when(dedupeService.isDuplicate("evt-cc")).thenReturn(false);
        when(costCenterRepository.findById("cc-1")).thenReturn(Optional.of(existing));

        MasterChangeCommand cmd = new MasterChangeCommand("evt-cc",
                "erp.masterdata.costcenter.changed.v1", "cc-1", ChangeKind.UPDATED, T0,
                Map.of("code", "CC-100", "name", "new", "departmentId", "dept-2", "status", "ACTIVE"));
        useCase.applyCostCenter(cmd);

        assertThat(existing.name()).isEqualTo("new");
        assertThat(existing.departmentId()).isEqualTo("dept-2");
        verify(costCenterRepository).save(existing);
        verify(dedupeService).markProcessed("evt-cc",
                "erp.masterdata.costcenter.changed.v1", "cc-1");
    }
}
