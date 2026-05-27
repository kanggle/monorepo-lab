package com.example.erp.masterdata.application;

import com.example.common.id.UuidV7;
import com.example.erp.masterdata.application.command.Commands.CreateBusinessPartnerCommand;
import com.example.erp.masterdata.application.command.Commands.CreateCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.CreateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.CreateJobGradeCommand;
import com.example.erp.masterdata.application.command.Commands.MoveDepartmentParentCommand;
import com.example.erp.masterdata.application.command.Commands.RetireBusinessPartnerCommand;
import com.example.erp.masterdata.application.command.Commands.RetireCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.RetireDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.RetireEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.RetireJobGradeCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateBusinessPartnerCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateCostCenterCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.UpdateJobGradeCommand;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher.ChangeKind;
import com.example.erp.masterdata.application.port.outbound.AuthorizationPort;
import com.example.erp.masterdata.application.port.outbound.ClockPort;
import com.example.erp.masterdata.application.view.BusinessPartnerView;
import com.example.erp.masterdata.application.view.CostCenterView;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.example.erp.masterdata.application.view.EmployeeView;
import com.example.erp.masterdata.application.view.JobGradeView;
import com.example.erp.masterdata.domain.audit.AuditLog;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.businesspartner.PartnerType;
import com.example.erp.masterdata.domain.businesspartner.PaymentTerms;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.DataScopeForbiddenException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataDuplicateKeyException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataNotFoundException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataParentCycleException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataReferenceViolationException;
import com.example.erp.masterdata.domain.error.DomainErrors.PermissionDeniedException;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * masterdata-service application service — the SINGLE {@code @Transactional}
 * command boundary (architecture.md § Layer Structure / § Boundary rules).
 * Every mutation use case invokes the {@link AuthorizationPort} BEFORE any
 * repository call, appends a single {@link AuditLog} row, and publishes an
 * {@code erp.masterdata.*.changed} event through the transactional outbox —
 * all in the same Tx (erp E2 / E6 / E8).
 *
 * <p>Controllers never carry {@code @Transactional} and never touch JPA
 * repositories.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterdataApplicationService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final JobGradeRepository jobGradeRepository;
    private final CostCenterRepository costCenterRepository;
    private final BusinessPartnerRepository businessPartnerRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuthorizationPort authorizationPort;
    private final ClockPort clock;
    private final MasterdataEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // ====================================================================
    // Department
    // ====================================================================

    @Transactional
    public DepartmentView createDepartment(CreateDepartmentCommand cmd) {
        ActorContext actor = cmd.actor();
        authorize(actor, RequiredScope.WRITE, cmd.parentId());
        Instant now = clock.now();

        departmentRepository.findByCode(cmd.code(), actor.tenantId()).ifPresent(existing -> {
            throw new MasterdataDuplicateKeyException(
                    "Department code already in use: " + cmd.code());
        });

        if (cmd.parentId() != null) {
            departmentRepository.findById(cmd.parentId(), actor.tenantId())
                    .orElseThrow(() -> new MasterdataNotFoundException(
                            "Parent department not found: " + cmd.parentId()));
        }

        LocalDate from = effectiveFrom(cmd.effectiveFrom(), now);
        EffectivePeriod period = EffectivePeriod.openEnded(from);

        Department department = Department.create(UuidV7.randomString(),
                actor.tenantId(), cmd.code(), cmd.name(), cmd.parentId(), period, now);
        Department saved = departmentRepository.save(department);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_DEPARTMENT, saved.getId(), "CREATE_DEPARTMENT", null, after, null, now);
        eventPublisher.publishDepartmentChanged(saved, ChangeKind.CREATED,
                actor.actorId(), null, after, null);
        return DepartmentView.from(saved);
    }

    @Transactional
    public DepartmentView updateDepartment(UpdateDepartmentCommand cmd) {
        ActorContext actor = cmd.actor();
        Department existing = loadOrThrow(departmentRepository::findById, cmd.id(), actor.tenantId(), "Department");
        authorize(actor, RequiredScope.WRITE, existing.getParentId());
        Instant now = clock.now();

        Map<String, Object> before = snapshot(existing);
        if (cmd.name() != null) {
            existing.rename(cmd.name(), now);
        }
        Department saved = departmentRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_DEPARTMENT, saved.getId(), "UPDATE_DEPARTMENT", before, after, null, now);
        eventPublisher.publishDepartmentChanged(saved, ChangeKind.UPDATED,
                actor.actorId(), before, after, null);
        return DepartmentView.from(saved);
    }

    @Transactional
    public DepartmentView retireDepartment(RetireDepartmentCommand cmd) {
        ActorContext actor = cmd.actor();
        Department existing = loadOrThrow(departmentRepository::findById, cmd.id(), actor.tenantId(), "Department");
        authorize(actor, RequiredScope.WRITE, existing.getParentId());
        Instant now = clock.now();

        // Reference integrity (E1) — block retire if any live referencer exists.
        ensureNoActiveReferencer(
                () -> departmentRepository.findActiveChildren(existing.getId(), actor.tenantId()),
                "Department " + existing.getId()
                        + " has active child departments — cannot retire");
        ensureNoActiveReferencer(
                () -> employeeRepository.findActiveByDepartmentId(existing.getId(), actor.tenantId()),
                "Department " + existing.getId()
                        + " is referenced by active employees — cannot retire");
        ensureNoActiveReferencer(
                () -> costCenterRepository.findActiveByDepartmentId(existing.getId(), actor.tenantId()),
                "Department " + existing.getId()
                        + " is referenced by active cost centers — cannot retire");

        Map<String, Object> before = snapshot(existing);
        existing.retire(now);
        Department saved = departmentRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_DEPARTMENT, saved.getId(), "RETIRE_DEPARTMENT", before, after, cmd.reason(), now);
        eventPublisher.publishDepartmentChanged(saved, ChangeKind.RETIRED,
                actor.actorId(), before, null, cmd.reason());
        return DepartmentView.from(saved);
    }

    @Transactional
    public DepartmentView moveDepartmentParent(MoveDepartmentParentCommand cmd) {
        ActorContext actor = cmd.actor();
        Department existing = loadOrThrow(departmentRepository::findById, cmd.id(), actor.tenantId(), "Department");
        authorize(actor, RequiredScope.WRITE, existing.getParentId());
        Instant now = clock.now();

        if (cmd.newParentId() != null) {
            Department newParent = departmentRepository.findById(cmd.newParentId(), actor.tenantId())
                    .orElseThrow(() -> new MasterdataNotFoundException(
                            "New parent department not found: " + cmd.newParentId()));
            // Cycle guard — walk ancestry of new parent; refuse if existing.id is on the path.
            List<Department> ancestors = departmentRepository.ancestors(
                    newParent.getId(), actor.tenantId());
            for (Department ancestor : ancestors) {
                if (ancestor.getId().equals(existing.getId())) {
                    throw new MasterdataParentCycleException(
                            "Move would create a cycle: department " + existing.getId()
                                    + " is an ancestor of new parent " + cmd.newParentId());
                }
            }
            if (cmd.newParentId().equals(existing.getId())) {
                throw new MasterdataParentCycleException(
                        "Department " + existing.getId() + " cannot be its own parent");
            }
        }

        Map<String, Object> before = snapshot(existing);
        existing.updateParent(cmd.newParentId(), now);
        Department saved = departmentRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_DEPARTMENT, saved.getId(), "MOVE_PARENT", before, after, cmd.reason(), now);
        eventPublisher.publishDepartmentChanged(saved, ChangeKind.PARENT_MOVED,
                actor.actorId(), before, after, cmd.reason());
        return DepartmentView.from(saved);
    }

    @Transactional(readOnly = true)
    public DepartmentView getDepartment(String id, ActorContext actor, LocalDate asOf) {
        Department d = loadOrThrow(departmentRepository::findById, id, actor.tenantId(), "Department");
        authorize(actor, RequiredScope.READ, d.getParentId());
        ensureEffectiveAt(d.period(), asOf, "Department", id);
        return DepartmentView.from(d);
    }

    @Transactional(readOnly = true)
    public List<DepartmentView> listDepartments(ActorContext actor, int page, int size) {
        authorize(actor, RequiredScope.READ, null);
        return departmentRepository.findAll(actor.tenantId(), page, size)
                .stream().map(DepartmentView::from).toList();
    }

    // ====================================================================
    // Employee
    // ====================================================================

    @Transactional
    public EmployeeView createEmployee(CreateEmployeeCommand cmd) {
        ActorContext actor = cmd.actor();
        authorize(actor, RequiredScope.WRITE, cmd.departmentId());
        Instant now = clock.now();

        employeeRepository.findByEmployeeNumber(cmd.employeeNumber(), actor.tenantId())
                .ifPresent(existing -> {
                    throw new MasterdataDuplicateKeyException(
                            "Employee number already in use: " + cmd.employeeNumber());
                });

        ensureActive(departmentRepository::findById, Department::isActive, cmd.departmentId(), actor.tenantId(), "Department");
        ensureActive(costCenterRepository::findById, CostCenter::isActive, cmd.costCenterId(), actor.tenantId(), "CostCenter");
        ensureActive(jobGradeRepository::findById, JobGrade::isActive, cmd.jobGradeId(), actor.tenantId(), "JobGrade");

        LocalDate from = effectiveFrom(cmd.effectiveFrom(), now);
        Employee e = Employee.create(UuidV7.randomString(), actor.tenantId(),
                cmd.employeeNumber(), cmd.name(), cmd.departmentId(),
                cmd.costCenterId(), cmd.jobGradeId(), EffectivePeriod.openEnded(from), now);
        Employee saved = employeeRepository.save(e);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_EMPLOYEE, saved.getId(), "CREATE_EMPLOYEE", null, after, null, now);
        eventPublisher.publishEmployeeChanged(saved, ChangeKind.CREATED,
                actor.actorId(), null, after, null);
        return EmployeeView.from(saved);
    }

    @Transactional
    public EmployeeView updateEmployee(UpdateEmployeeCommand cmd) {
        ActorContext actor = cmd.actor();
        Employee existing = loadOrThrow(employeeRepository::findById, cmd.id(), actor.tenantId(), "Employee");
        authorize(actor, RequiredScope.WRITE, existing.getDepartmentId());
        Instant now = clock.now();

        if (cmd.departmentId() != null) {
            ensureActive(departmentRepository::findById, Department::isActive, cmd.departmentId(), actor.tenantId(), "Department");
        }
        if (cmd.costCenterId() != null) {
            ensureActive(costCenterRepository::findById, CostCenter::isActive, cmd.costCenterId(), actor.tenantId(), "CostCenter");
        }
        if (cmd.jobGradeId() != null) {
            ensureActive(jobGradeRepository::findById, JobGrade::isActive, cmd.jobGradeId(), actor.tenantId(), "JobGrade");
        }

        Map<String, Object> before = snapshot(existing);
        existing.updateAttributes(cmd.name(), cmd.departmentId(),
                cmd.costCenterId(), cmd.jobGradeId(), now);
        Employee saved = employeeRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_EMPLOYEE, saved.getId(), "UPDATE_EMPLOYEE", before, after, null, now);
        eventPublisher.publishEmployeeChanged(saved, ChangeKind.UPDATED,
                actor.actorId(), before, after, null);
        return EmployeeView.from(saved);
    }

    @Transactional
    public EmployeeView retireEmployee(RetireEmployeeCommand cmd) {
        ActorContext actor = cmd.actor();
        Employee existing = loadOrThrow(employeeRepository::findById, cmd.id(), actor.tenantId(), "Employee");
        authorize(actor, RequiredScope.WRITE, existing.getDepartmentId());
        Instant now = clock.now();

        Map<String, Object> before = snapshot(existing);
        existing.retire(now);
        Employee saved = employeeRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_EMPLOYEE, saved.getId(), "RETIRE_EMPLOYEE", before, after, cmd.reason(), now);
        eventPublisher.publishEmployeeChanged(saved, ChangeKind.RETIRED,
                actor.actorId(), before, null, cmd.reason());
        return EmployeeView.from(saved);
    }

    @Transactional(readOnly = true)
    public EmployeeView getEmployee(String id, ActorContext actor, LocalDate asOf) {
        Employee e = loadOrThrow(employeeRepository::findById, id, actor.tenantId(), "Employee");
        authorize(actor, RequiredScope.READ, e.getDepartmentId());
        ensureEffectiveAt(e.period(), asOf, "Employee", id);
        return EmployeeView.from(e);
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> listEmployees(ActorContext actor, int page, int size) {
        authorize(actor, RequiredScope.READ, null);
        return employeeRepository.findAll(actor.tenantId(), page, size)
                .stream().map(EmployeeView::from).toList();
    }

    // ====================================================================
    // JobGrade
    // ====================================================================

    @Transactional
    public JobGradeView createJobGrade(CreateJobGradeCommand cmd) {
        ActorContext actor = cmd.actor();
        authorize(actor, RequiredScope.WRITE, null);
        Instant now = clock.now();

        jobGradeRepository.findByCode(cmd.code(), actor.tenantId()).ifPresent(existing -> {
            throw new MasterdataDuplicateKeyException(
                    "JobGrade code already in use: " + cmd.code());
        });

        LocalDate from = effectiveFrom(cmd.effectiveFrom(), now);
        JobGrade g = JobGrade.create(UuidV7.randomString(), actor.tenantId(),
                cmd.code(), cmd.name(), cmd.displayOrder(),
                EffectivePeriod.openEnded(from), now);
        JobGrade saved = jobGradeRepository.save(g);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_JOBGRADE, saved.getId(), "CREATE_JOBGRADE", null, after, null, now);
        eventPublisher.publishJobGradeChanged(saved, ChangeKind.CREATED,
                actor.actorId(), null, after, null);
        return JobGradeView.from(saved);
    }

    @Transactional
    public JobGradeView updateJobGrade(UpdateJobGradeCommand cmd) {
        ActorContext actor = cmd.actor();
        JobGrade existing = loadOrThrow(jobGradeRepository::findById, cmd.id(), actor.tenantId(), "JobGrade");
        authorize(actor, RequiredScope.WRITE, null);
        Instant now = clock.now();

        Map<String, Object> before = snapshot(existing);
        existing.updateAttributes(cmd.name(), cmd.displayOrder(), now);
        JobGrade saved = jobGradeRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_JOBGRADE, saved.getId(), "UPDATE_JOBGRADE", before, after, null, now);
        eventPublisher.publishJobGradeChanged(saved, ChangeKind.UPDATED,
                actor.actorId(), before, after, null);
        return JobGradeView.from(saved);
    }

    @Transactional
    public JobGradeView retireJobGrade(RetireJobGradeCommand cmd) {
        ActorContext actor = cmd.actor();
        JobGrade existing = loadOrThrow(jobGradeRepository::findById, cmd.id(), actor.tenantId(), "JobGrade");
        authorize(actor, RequiredScope.WRITE, null);
        Instant now = clock.now();

        ensureNoActiveReferencer(
                () -> employeeRepository.findActiveByJobGradeId(existing.getId(), actor.tenantId()),
                "JobGrade " + existing.getId()
                        + " is referenced by active employees — cannot retire");

        Map<String, Object> before = snapshot(existing);
        existing.retire(now);
        JobGrade saved = jobGradeRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_JOBGRADE, saved.getId(), "RETIRE_JOBGRADE", before, after, cmd.reason(), now);
        eventPublisher.publishJobGradeChanged(saved, ChangeKind.RETIRED,
                actor.actorId(), before, null, cmd.reason());
        return JobGradeView.from(saved);
    }

    @Transactional(readOnly = true)
    public JobGradeView getJobGrade(String id, ActorContext actor, LocalDate asOf) {
        authorize(actor, RequiredScope.READ, null);
        JobGrade g = loadOrThrow(jobGradeRepository::findById, id, actor.tenantId(), "JobGrade");
        ensureEffectiveAt(g.period(), asOf, "JobGrade", id);
        return JobGradeView.from(g);
    }

    @Transactional(readOnly = true)
    public List<JobGradeView> listJobGrades(ActorContext actor, int page, int size) {
        authorize(actor, RequiredScope.READ, null);
        return jobGradeRepository.findAll(actor.tenantId(), page, size)
                .stream().map(JobGradeView::from).toList();
    }

    // ====================================================================
    // CostCenter
    // ====================================================================

    @Transactional
    public CostCenterView createCostCenter(CreateCostCenterCommand cmd) {
        ActorContext actor = cmd.actor();
        authorize(actor, RequiredScope.WRITE, cmd.departmentId());
        Instant now = clock.now();

        costCenterRepository.findByCode(cmd.code(), actor.tenantId()).ifPresent(existing -> {
            throw new MasterdataDuplicateKeyException(
                    "CostCenter code already in use: " + cmd.code());
        });
        ensureActive(departmentRepository::findById, Department::isActive, cmd.departmentId(), actor.tenantId(), "Department");

        LocalDate from = effectiveFrom(cmd.effectiveFrom(), now);
        CostCenter c = CostCenter.create(UuidV7.randomString(), actor.tenantId(),
                cmd.code(), cmd.name(), cmd.departmentId(),
                EffectivePeriod.openEnded(from), now);
        CostCenter saved = costCenterRepository.save(c);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_COSTCENTER, saved.getId(), "CREATE_COSTCENTER", null, after, null, now);
        eventPublisher.publishCostCenterChanged(saved, ChangeKind.CREATED,
                actor.actorId(), null, after, null);
        return CostCenterView.from(saved);
    }

    @Transactional
    public CostCenterView updateCostCenter(UpdateCostCenterCommand cmd) {
        ActorContext actor = cmd.actor();
        CostCenter existing = loadOrThrow(costCenterRepository::findById, cmd.id(), actor.tenantId(), "CostCenter");
        authorize(actor, RequiredScope.WRITE, existing.getDepartmentId());
        Instant now = clock.now();

        if (cmd.departmentId() != null) {
            ensureActive(departmentRepository::findById, Department::isActive, cmd.departmentId(), actor.tenantId(), "Department");
        }

        Map<String, Object> before = snapshot(existing);
        existing.updateAttributes(cmd.name(), cmd.departmentId(), now);
        CostCenter saved = costCenterRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_COSTCENTER, saved.getId(), "UPDATE_COSTCENTER", before, after, null, now);
        eventPublisher.publishCostCenterChanged(saved, ChangeKind.UPDATED,
                actor.actorId(), before, after, null);
        return CostCenterView.from(saved);
    }

    @Transactional
    public CostCenterView retireCostCenter(RetireCostCenterCommand cmd) {
        ActorContext actor = cmd.actor();
        CostCenter existing = loadOrThrow(costCenterRepository::findById, cmd.id(), actor.tenantId(), "CostCenter");
        authorize(actor, RequiredScope.WRITE, existing.getDepartmentId());
        Instant now = clock.now();

        ensureNoActiveReferencer(
                () -> employeeRepository.findActiveByCostCenterId(existing.getId(), actor.tenantId()),
                "CostCenter " + existing.getId()
                        + " is referenced by active employees — cannot retire");

        Map<String, Object> before = snapshot(existing);
        existing.retire(now);
        CostCenter saved = costCenterRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_COSTCENTER, saved.getId(), "RETIRE_COSTCENTER", before, after, cmd.reason(), now);
        eventPublisher.publishCostCenterChanged(saved, ChangeKind.RETIRED,
                actor.actorId(), before, null, cmd.reason());
        return CostCenterView.from(saved);
    }

    @Transactional(readOnly = true)
    public CostCenterView getCostCenter(String id, ActorContext actor, LocalDate asOf) {
        CostCenter c = loadOrThrow(costCenterRepository::findById, id, actor.tenantId(), "CostCenter");
        authorize(actor, RequiredScope.READ, c.getDepartmentId());
        ensureEffectiveAt(c.period(), asOf, "CostCenter", id);
        return CostCenterView.from(c);
    }

    @Transactional(readOnly = true)
    public List<CostCenterView> listCostCenters(ActorContext actor, int page, int size) {
        authorize(actor, RequiredScope.READ, null);
        return costCenterRepository.findAll(actor.tenantId(), page, size)
                .stream().map(CostCenterView::from).toList();
    }

    // ====================================================================
    // BusinessPartner
    // ====================================================================

    @Transactional
    public BusinessPartnerView createBusinessPartner(CreateBusinessPartnerCommand cmd) {
        ActorContext actor = cmd.actor();
        authorize(actor, RequiredScope.WRITE, null);
        Instant now = clock.now();

        businessPartnerRepository.findByCode(cmd.code(), actor.tenantId()).ifPresent(existing -> {
            throw new MasterdataDuplicateKeyException(
                    "BusinessPartner code already in use: " + cmd.code());
        });

        PartnerType type = PartnerType.valueOf(cmd.partnerType().trim().toUpperCase());
        PaymentTerms terms = PaymentTerms.of(cmd.paymentTermDays(),
                PaymentTerms.PaymentMethod.valueOf(cmd.paymentMethod().trim().toUpperCase()));

        LocalDate from = effectiveFrom(cmd.effectiveFrom(), now);
        BusinessPartner b = BusinessPartner.create(UuidV7.randomString(), actor.tenantId(),
                cmd.code(), cmd.name(), type, terms, EffectivePeriod.openEnded(from), now);
        BusinessPartner saved = businessPartnerRepository.save(b);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_BUSINESSPARTNER, saved.getId(), "CREATE_BUSINESSPARTNER",
                null, after, null, now);
        eventPublisher.publishBusinessPartnerChanged(saved, ChangeKind.CREATED,
                actor.actorId(), null, after, null);
        return BusinessPartnerView.from(saved);
    }

    @Transactional
    public BusinessPartnerView updateBusinessPartner(UpdateBusinessPartnerCommand cmd) {
        ActorContext actor = cmd.actor();
        BusinessPartner existing = loadOrThrow(businessPartnerRepository::findById, cmd.id(), actor.tenantId(), "BusinessPartner");
        authorize(actor, RequiredScope.WRITE, null);
        Instant now = clock.now();

        PartnerType type = cmd.partnerType() == null ? null
                : PartnerType.valueOf(cmd.partnerType().trim().toUpperCase());
        PaymentTerms newTerms = (cmd.paymentTermDays() != null && cmd.paymentMethod() != null)
                ? PaymentTerms.of(cmd.paymentTermDays(),
                        PaymentTerms.PaymentMethod.valueOf(cmd.paymentMethod().trim().toUpperCase()))
                : null;

        Map<String, Object> before = snapshot(existing);
        existing.updateAttributes(cmd.name(), type, newTerms, now);
        BusinessPartner saved = businessPartnerRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_BUSINESSPARTNER, saved.getId(), "UPDATE_BUSINESSPARTNER",
                before, after, null, now);
        eventPublisher.publishBusinessPartnerChanged(saved, ChangeKind.UPDATED,
                actor.actorId(), before, after, null);
        return BusinessPartnerView.from(saved);
    }

    @Transactional
    public BusinessPartnerView retireBusinessPartner(RetireBusinessPartnerCommand cmd) {
        ActorContext actor = cmd.actor();
        BusinessPartner existing = loadOrThrow(businessPartnerRepository::findById, cmd.id(), actor.tenantId(), "BusinessPartner");
        authorize(actor, RequiredScope.WRITE, null);
        Instant now = clock.now();

        Map<String, Object> before = snapshot(existing);
        existing.retire(now);
        BusinessPartner saved = businessPartnerRepository.save(existing);

        Map<String, Object> after = snapshot(saved);
        audit(actor, MasterdataEventPublisher.AGG_BUSINESSPARTNER, saved.getId(), "RETIRE_BUSINESSPARTNER",
                before, after, cmd.reason(), now);
        eventPublisher.publishBusinessPartnerChanged(saved, ChangeKind.RETIRED,
                actor.actorId(), before, null, cmd.reason());
        return BusinessPartnerView.from(saved);
    }

    @Transactional(readOnly = true)
    public BusinessPartnerView getBusinessPartner(String id, ActorContext actor, LocalDate asOf) {
        authorize(actor, RequiredScope.READ, null);
        BusinessPartner b = loadOrThrow(businessPartnerRepository::findById, id, actor.tenantId(), "BusinessPartner");
        ensureEffectiveAt(b.period(), asOf, "BusinessPartner", id);
        return BusinessPartnerView.from(b);
    }

    @Transactional(readOnly = true)
    public List<BusinessPartnerView> listBusinessPartners(ActorContext actor, int page, int size) {
        authorize(actor, RequiredScope.READ, null);
        return businessPartnerRepository.findAll(actor.tenantId(), page, size)
                .stream().map(BusinessPartnerView::from).toList();
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private void authorize(ActorContext actor, RequiredScope scope, String targetDepartmentId) {
        AuthorizationDecision d = authorizationPort.evaluate(actor, scope, targetDepartmentId);
        if (d.outcome() == AuthorizationDecision.Outcome.DENY_ROLE) {
            throw new PermissionDeniedException(d.reason() == null
                    ? "required role not present" : d.reason());
        }
        if (d.outcome() == AuthorizationDecision.Outcome.DENY_SCOPE) {
            throw new DataScopeForbiddenException(d.reason() == null
                    ? "target outside data scope" : d.reason());
        }
    }

    private LocalDate effectiveFrom(LocalDate provided, Instant now) {
        return provided != null ? provided : now.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * Point-in-time existence check (architecture.md § Effective dating). For
     * v1 the row IS the current revision; an {@code asOf} that falls outside
     * its period returns NOT_FOUND. Shared across all five master {@code get*}
     * use cases.
     */
    private void ensureEffectiveAt(EffectivePeriod period, LocalDate asOf,
                                   String label, String id) {
        if (asOf != null && !period.contains(asOf)) {
            throw new MasterdataNotFoundException(
                    label + " " + id + " has no effective revision at " + asOf);
        }
    }

    /**
     * Generic master-row loader. Parallel to {@link #ensureActive} — each
     * use case passes the repo's {@code findById} method reference and the
     * entity label used in the NOT_FOUND message. Removes five typed
     * {@code load<Entity>} helpers that differed only in entity type + label.
     */
    private <T> T loadOrThrow(BiFunction<String, String, Optional<T>> finder,
                              String id, String tenantId, String label) {
        return finder.apply(id, tenantId)
                .orElseThrow(() -> new MasterdataNotFoundException(
                        label + " not found: " + id));
    }

    /**
     * Reference-integrity guard (erp E1). If the supplied finder returns any
     * live referencer, the retire is blocked with the contract message. Shared
     * across Department / JobGrade / CostCenter retire paths.
     */
    private void ensureNoActiveReferencer(Supplier<List<?>> finder, String message) {
        if (!finder.get().isEmpty()) {
            throw new MasterdataReferenceViolationException(message);
        }
    }

    private <T> void ensureActive(BiFunction<String, String, Optional<T>> finder,
                                   Predicate<T> activeCheck,
                                   String id, String tenantId, String label) {
        T entity = finder.apply(id, tenantId)
                .orElseThrow(() -> new MasterdataNotFoundException(
                        label + " not found: " + id));
        if (!activeCheck.test(entity)) {
            throw new MasterdataNotFoundException(
                    label + " " + id + " is not ACTIVE — cannot reference");
        }
    }

    private void audit(ActorContext actor, String aggType, String aggId, String action,
                       Map<String, Object> before, Map<String, Object> after,
                       String reason, Instant now) {
        auditLogRepository.append(AuditLog.of(actor.tenantId(), aggType, aggId, action,
                actor.actorId(), toJson(before), toJson(after), reason, now));
    }

    private String toJson(Map<String, Object> m) {
        if (m == null) return null;
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize audit snapshot", e);
        }
    }

    private static Map<String, Object> snapshot(Department d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("parentId", d.getParentId());
        m.put("status", d.getStatus().name());
        m.put("effectiveFrom", d.getEffectiveFrom() == null ? null : d.getEffectiveFrom().toString());
        m.put("effectiveTo", d.getEffectiveTo() == null ? null : d.getEffectiveTo().toString());
        return m;
    }

    private static Map<String, Object> snapshot(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeNumber", e.getEmployeeNumber());
        m.put("name", e.getName());
        m.put("departmentId", e.getDepartmentId());
        m.put("costCenterId", e.getCostCenterId());
        m.put("jobGradeId", e.getJobGradeId());
        m.put("status", e.getStatus().name());
        m.put("effectiveFrom", e.getEffectiveFrom() == null ? null : e.getEffectiveFrom().toString());
        m.put("effectiveTo", e.getEffectiveTo() == null ? null : e.getEffectiveTo().toString());
        return m;
    }

    private static Map<String, Object> snapshot(JobGrade g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", g.getCode());
        m.put("name", g.getName());
        m.put("displayOrder", g.getDisplayOrder());
        m.put("status", g.getStatus().name());
        m.put("effectiveFrom", g.getEffectiveFrom() == null ? null : g.getEffectiveFrom().toString());
        m.put("effectiveTo", g.getEffectiveTo() == null ? null : g.getEffectiveTo().toString());
        return m;
    }

    private static Map<String, Object> snapshot(CostCenter c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", c.getCode());
        m.put("name", c.getName());
        m.put("departmentId", c.getDepartmentId());
        m.put("status", c.getStatus().name());
        m.put("effectiveFrom", c.getEffectiveFrom() == null ? null : c.getEffectiveFrom().toString());
        m.put("effectiveTo", c.getEffectiveTo() == null ? null : c.getEffectiveTo().toString());
        return m;
    }

    private static Map<String, Object> snapshot(BusinessPartner b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", b.getCode());
        m.put("name", b.getName());
        m.put("partnerType", b.getPartnerType().name());
        if (b.getPaymentTerms() != null) {
            Map<String, Object> pt = new LinkedHashMap<>();
            pt.put("termDays", b.getPaymentTerms().getTermDays());
            pt.put("method", b.getPaymentTerms().getMethod() == null ? null
                    : b.getPaymentTerms().getMethod().name());
            m.put("paymentTerms", pt);
        }
        m.put("status", b.getStatus().name());
        m.put("effectiveFrom", b.getEffectiveFrom() == null ? null : b.getEffectiveFrom().toString());
        m.put("effectiveTo", b.getEffectiveTo() == null ? null : b.getEffectiveTo().toString());
        return m;
    }
}
