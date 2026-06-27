package com.example.erp.masterdata.application.event;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import java.util.Map;

/**
 * Outbox write-path port for masterdata-service domain events (TASK-ERP-BE-026 —
 * outbox v1 → v2). Appends {@code erp.masterdata.<aggregate>.changed} events to
 * the transactional outbox. Every {@code publish*} call happens INSIDE the
 * use-case {@code @Transactional} boundary so the event write commits atomically
 * with the master mutation + audit_log row (erp E2 atomicity).
 *
 * <p>The v1 implementation extended {@code BaseEventPublisher} (lib
 * {@code OutboxWriter} → {@code outbox} table). The v2 implementation
 * {@link com.example.erp.masterdata.infrastructure.outbox.OutboxMasterdataEventPublisher}
 * builds the canonical 7-field envelope and persists a {@code masterdata_outbox}
 * row; {@code MasterdataOutboxPublisher} relays it. The application layer + caller
 * unit tests are unchanged (they mock this port).
 *
 * <p>Contract: {@code specs/contracts/events/erp-masterdata-events.md}.
 */
public interface MasterdataEventPublisher {

    String EVENT_DEPARTMENT_CHANGED = "erp.masterdata.department.changed";
    String EVENT_EMPLOYEE_CHANGED = "erp.masterdata.employee.changed";
    String EVENT_JOBGRADE_CHANGED = "erp.masterdata.jobgrade.changed";
    String EVENT_COSTCENTER_CHANGED = "erp.masterdata.costcenter.changed";
    String EVENT_BUSINESSPARTNER_CHANGED = "erp.masterdata.businesspartner.changed";

    String AGG_DEPARTMENT = "department";
    String AGG_EMPLOYEE = "employee";
    String AGG_JOBGRADE = "jobgrade";
    String AGG_COSTCENTER = "costcenter";
    String AGG_BUSINESSPARTNER = "businesspartner";

    enum ChangeKind {CREATED, UPDATED, RETIRED, PARENT_MOVED}

    void publishDepartmentChanged(Department d, ChangeKind kind, String actor,
                                  Map<String, Object> before,
                                  Map<String, Object> after, String reason);

    void publishEmployeeChanged(Employee e, ChangeKind kind, String actor,
                                Map<String, Object> before,
                                Map<String, Object> after, String reason);

    void publishJobGradeChanged(JobGrade g, ChangeKind kind, String actor,
                                Map<String, Object> before,
                                Map<String, Object> after, String reason);

    void publishCostCenterChanged(CostCenter c, ChangeKind kind, String actor,
                                  Map<String, Object> before,
                                  Map<String, Object> after, String reason);

    void publishBusinessPartnerChanged(BusinessPartner b, ChangeKind kind, String actor,
                                       Map<String, Object> before,
                                       Map<String, Object> after, String reason);
}
