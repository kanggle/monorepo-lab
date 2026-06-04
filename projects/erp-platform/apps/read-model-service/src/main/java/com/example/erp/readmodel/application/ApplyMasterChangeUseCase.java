package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.command.MasterChangeCommand;
import com.example.erp.readmodel.domain.common.ChangeKind;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.CostCenterProjection;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.JobGradeProjection;
import com.example.erp.readmodel.domain.projection.repository.CostCenterProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.JobGradeProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Applies a single masterdata change event to the matching projection table
 * (E5 read-only projection). One transaction per event: dedupe check →
 * upsert / retire-mark → record provenance. {@code CREATED}/{@code UPDATED}/
 * {@code PARENT_MOVED} upsert the latest {@code after}; {@code RETIRED} marks
 * {@code status=RETIRED} + {@code effectiveTo} (logical retire, row retained).
 *
 * <p>Each handler is an idempotent single-table upsert keyed by
 * {@code aggregateId} — no fan-out re-stamp on a department rename (the org-view
 * join is read-time). A duplicate {@code eventId} (already in
 * {@code processed_events}) is a no-op (T8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyMasterChangeUseCase {

    private final DepartmentProjectionRepository departmentRepository;
    private final EmployeeProjectionRepository employeeRepository;
    private final JobGradeProjectionRepository jobGradeRepository;
    private final CostCenterProjectionRepository costCenterRepository;
    private final EventDedupeService dedupeService;

    @Transactional
    public void applyDepartment(MasterChangeCommand cmd) {
        if (skipDuplicate(cmd)) {
            return;
        }
        Instant at = cmd.occurredAt();
        String eventId = cmd.eventId();
        String id = cmd.aggregateId();
        if (cmd.changeKind() == ChangeKind.RETIRED) {
            departmentRepository.findById(id).ifPresentOrElse(
                    existing -> {
                        existing.applyRetire(cmd.effectiveDate("effectiveTo"), at, eventId);
                        departmentRepository.save(existing);
                    },
                    () -> log.warn("RETIRED department {} not previously projected; eventId={}", id, eventId));
        } else {
            DepartmentProjection projection = departmentRepository.findById(id).orElse(null);
            String code = cmd.afterString("code");
            String name = cmd.afterString("name");
            String parentId = cmd.afterString("parentId");
            MasterStatus status = MasterStatus.fromOrActive(cmd.afterString("status"));
            LocalDate from = cmd.effectiveDate("effectiveFrom");
            LocalDate to = cmd.effectiveDate("effectiveTo");
            if (projection == null) {
                departmentRepository.save(DepartmentProjection.of(
                        id, code, name, parentId, status, from, to, at, eventId));
            } else {
                projection.applyUpsert(code, name, parentId, status, from, to, at, eventId);
                departmentRepository.save(projection);
            }
        }
        dedupeService.markProcessed(eventId, cmd.topic(), id);
    }

    @Transactional
    public void applyEmployee(MasterChangeCommand cmd) {
        if (skipDuplicate(cmd)) {
            return;
        }
        Instant at = cmd.occurredAt();
        String eventId = cmd.eventId();
        String id = cmd.aggregateId();
        if (cmd.changeKind() == ChangeKind.RETIRED) {
            employeeRepository.findById(id).ifPresent(existing -> {
                existing.applyRetire(cmd.effectiveDate("effectiveTo"), at, eventId);
                employeeRepository.save(existing);
            });
        } else {
            EmployeeProjection projection = employeeRepository.findById(id).orElse(null);
            String employeeNumber = cmd.afterString("employeeNumber");
            String name = cmd.afterString("name");
            String departmentId = cmd.afterString("departmentId");
            String costCenterId = cmd.afterString("costCenterId");
            String jobGradeId = cmd.afterString("jobGradeId");
            MasterStatus status = MasterStatus.fromOrActive(cmd.afterString("status"));
            LocalDate from = cmd.effectiveDate("effectiveFrom");
            LocalDate to = cmd.effectiveDate("effectiveTo");
            if (projection == null) {
                employeeRepository.save(EmployeeProjection.of(
                        id, employeeNumber, name, departmentId, costCenterId, jobGradeId,
                        status, from, to, at, eventId));
            } else {
                projection.applyUpsert(employeeNumber, name, departmentId, costCenterId,
                        jobGradeId, status, from, to, at, eventId);
                employeeRepository.save(projection);
            }
        }
        dedupeService.markProcessed(eventId, cmd.topic(), id);
    }

    @Transactional
    public void applyJobGrade(MasterChangeCommand cmd) {
        if (skipDuplicate(cmd)) {
            return;
        }
        Instant at = cmd.occurredAt();
        String eventId = cmd.eventId();
        String id = cmd.aggregateId();
        if (cmd.changeKind() == ChangeKind.RETIRED) {
            jobGradeRepository.findById(id).ifPresent(existing -> {
                existing.applyRetire(cmd.effectiveDate("effectiveTo"), at, eventId);
                jobGradeRepository.save(existing);
            });
        } else {
            JobGradeProjection projection = jobGradeRepository.findById(id).orElse(null);
            String code = cmd.afterString("code");
            String name = cmd.afterString("name");
            int displayOrder = cmd.afterInt("displayOrder", 0);
            MasterStatus status = MasterStatus.fromOrActive(cmd.afterString("status"));
            LocalDate from = cmd.effectiveDate("effectiveFrom");
            LocalDate to = cmd.effectiveDate("effectiveTo");
            if (projection == null) {
                jobGradeRepository.save(JobGradeProjection.of(
                        id, code, name, displayOrder, status, from, to, at, eventId));
            } else {
                projection.applyUpsert(code, name, displayOrder, status, from, to, at, eventId);
                jobGradeRepository.save(projection);
            }
        }
        dedupeService.markProcessed(eventId, cmd.topic(), id);
    }

    @Transactional
    public void applyCostCenter(MasterChangeCommand cmd) {
        if (skipDuplicate(cmd)) {
            return;
        }
        Instant at = cmd.occurredAt();
        String eventId = cmd.eventId();
        String id = cmd.aggregateId();
        if (cmd.changeKind() == ChangeKind.RETIRED) {
            costCenterRepository.findById(id).ifPresent(existing -> {
                existing.applyRetire(cmd.effectiveDate("effectiveTo"), at, eventId);
                costCenterRepository.save(existing);
            });
        } else {
            CostCenterProjection projection = costCenterRepository.findById(id).orElse(null);
            String code = cmd.afterString("code");
            String name = cmd.afterString("name");
            String departmentId = cmd.afterString("departmentId");
            MasterStatus status = MasterStatus.fromOrActive(cmd.afterString("status"));
            LocalDate from = cmd.effectiveDate("effectiveFrom");
            LocalDate to = cmd.effectiveDate("effectiveTo");
            if (projection == null) {
                costCenterRepository.save(CostCenterProjection.of(
                        id, code, name, departmentId, status, from, to, at, eventId));
            } else {
                projection.applyUpsert(code, name, departmentId, status, from, to, at, eventId);
                costCenterRepository.save(projection);
            }
        }
        dedupeService.markProcessed(eventId, cmd.topic(), id);
    }

    private boolean skipDuplicate(MasterChangeCommand cmd) {
        if (dedupeService.isDuplicate(cmd.eventId())) {
            log.debug("Duplicate event skipped: eventId={} topic={}", cmd.eventId(), cmd.topic());
            return true;
        }
        return false;
    }
}
