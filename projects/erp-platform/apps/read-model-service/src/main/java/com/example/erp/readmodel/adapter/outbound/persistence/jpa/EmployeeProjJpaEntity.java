package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "employee_proj")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeProjJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "employee_number", nullable = false, length = 64)
    private String employeeNumber;

    @Column(name = "name", nullable = false, length = 256)
    private String name;

    @Column(name = "department_id", length = 36)
    private String departmentId;

    @Column(name = "cost_center_id", length = 36)
    private String costCenterId;

    @Column(name = "job_grade_id", length = 36)
    private String jobGradeId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "last_event_id", nullable = false, length = 64)
    private String lastEventId;
}
