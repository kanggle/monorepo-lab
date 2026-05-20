package com.example.erp.masterdata.application.event;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.costcenter.CostCenter;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends {@code erp.masterdata.<aggregate>.changed} events to the
 * transactional outbox (architecture.md § Outbox + audit_log invariants).
 * Every {@code publish*} call happens INSIDE the use-case
 * {@code @Transactional} boundary so the event write commits atomically with
 * the master mutation + audit_log row (erp E2 atomicity).
 *
 * <p>Contract: {@code specs/contracts/events/erp-masterdata-events.md}.
 */
@Component
public class MasterdataEventPublisher extends BaseEventPublisher {

    public static final String SOURCE = "erp-platform-masterdata-service";

    public static final String EVENT_DEPARTMENT_CHANGED = "erp.masterdata.department.changed";
    public static final String EVENT_EMPLOYEE_CHANGED = "erp.masterdata.employee.changed";
    public static final String EVENT_JOBGRADE_CHANGED = "erp.masterdata.jobgrade.changed";
    public static final String EVENT_COSTCENTER_CHANGED = "erp.masterdata.costcenter.changed";
    public static final String EVENT_BUSINESSPARTNER_CHANGED = "erp.masterdata.businesspartner.changed";

    public static final String AGG_DEPARTMENT = "department";
    public static final String AGG_EMPLOYEE = "employee";
    public static final String AGG_JOBGRADE = "jobgrade";
    public static final String AGG_COSTCENTER = "costcenter";
    public static final String AGG_BUSINESSPARTNER = "businesspartner";

    public enum ChangeKind {CREATED, UPDATED, RETIRED, PARENT_MOVED}

    public MasterdataEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishDepartmentChanged(Department d, ChangeKind kind, String actor,
                                          Map<String, Object> before,
                                          Map<String, Object> after, String reason) {
        writeEvent(AGG_DEPARTMENT, d.getId(), EVENT_DEPARTMENT_CHANGED, SOURCE,
                payload(d.getId(), d.getTenantId(), kind, actor, before, after, reason));
    }

    public void publishEmployeeChanged(Employee e, ChangeKind kind, String actor,
                                        Map<String, Object> before,
                                        Map<String, Object> after, String reason) {
        writeEvent(AGG_EMPLOYEE, e.getId(), EVENT_EMPLOYEE_CHANGED, SOURCE,
                payload(e.getId(), e.getTenantId(), kind, actor, before, after, reason));
    }

    public void publishJobGradeChanged(JobGrade g, ChangeKind kind, String actor,
                                        Map<String, Object> before,
                                        Map<String, Object> after, String reason) {
        writeEvent(AGG_JOBGRADE, g.getId(), EVENT_JOBGRADE_CHANGED, SOURCE,
                payload(g.getId(), g.getTenantId(), kind, actor, before, after, reason));
    }

    public void publishCostCenterChanged(CostCenter c, ChangeKind kind, String actor,
                                          Map<String, Object> before,
                                          Map<String, Object> after, String reason) {
        writeEvent(AGG_COSTCENTER, c.getId(), EVENT_COSTCENTER_CHANGED, SOURCE,
                payload(c.getId(), c.getTenantId(), kind, actor, before, after, reason));
    }

    public void publishBusinessPartnerChanged(BusinessPartner b, ChangeKind kind, String actor,
                                               Map<String, Object> before,
                                               Map<String, Object> after, String reason) {
        writeEvent(AGG_BUSINESSPARTNER, b.getId(), EVENT_BUSINESSPARTNER_CHANGED, SOURCE,
                payload(b.getId(), b.getTenantId(), kind, actor, before, after, reason));
    }

    private static Map<String, Object> payload(String aggregateId, String tenantId,
                                                ChangeKind kind, String actor,
                                                Map<String, Object> before,
                                                Map<String, Object> after, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("aggregateId", aggregateId);
        p.put("changeKind", kind.name());
        p.put("tenantId", tenantId);
        p.put("occurredAt", Instant.now().toString());
        p.put("actor", actor);
        p.put("before", before);
        p.put("after", after);
        p.put("reason", reason);
        return p;
    }
}
