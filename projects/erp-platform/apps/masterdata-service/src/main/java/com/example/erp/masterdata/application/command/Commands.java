package com.example.erp.masterdata.application.command;

import com.example.erp.masterdata.application.ActorContext;

import java.time.LocalDate;

/**
 * Command record container — one Command per use case (architecture.md §
 * Application layer). Grouped in one file to keep the command vocabulary
 * scannable; each is a distinct {@code record} so the application service can
 * dispatch on type.
 */
public final class Commands {

    private Commands() {
    }

    // ---- Department ----
    public record CreateDepartmentCommand(ActorContext actor, String code, String name,
                                          String parentId, LocalDate effectiveFrom) {
    }

    public record UpdateDepartmentCommand(ActorContext actor, String id, String name,
                                          LocalDate effectiveFrom) {
    }

    public record RetireDepartmentCommand(ActorContext actor, String id, String reason) {
    }

    public record MoveDepartmentParentCommand(ActorContext actor, String id,
                                              String newParentId,
                                              LocalDate effectiveFrom, String reason) {
    }

    // ---- Employee ----
    public record CreateEmployeeCommand(ActorContext actor, String employeeNumber,
                                        String name, String departmentId,
                                        String costCenterId, String jobGradeId,
                                        LocalDate effectiveFrom) {
    }

    public record UpdateEmployeeCommand(ActorContext actor, String id, String name,
                                        String departmentId, String costCenterId,
                                        String jobGradeId, LocalDate effectiveFrom) {
    }

    public record RetireEmployeeCommand(ActorContext actor, String id, String reason) {
    }

    // ---- JobGrade ----
    public record CreateJobGradeCommand(ActorContext actor, String code, String name,
                                        int displayOrder, LocalDate effectiveFrom) {
    }

    public record UpdateJobGradeCommand(ActorContext actor, String id, String name,
                                        Integer displayOrder, LocalDate effectiveFrom) {
    }

    public record RetireJobGradeCommand(ActorContext actor, String id, String reason) {
    }

    // ---- CostCenter ----
    public record CreateCostCenterCommand(ActorContext actor, String code, String name,
                                          String departmentId, LocalDate effectiveFrom) {
    }

    public record UpdateCostCenterCommand(ActorContext actor, String id, String name,
                                          String departmentId, LocalDate effectiveFrom) {
    }

    public record RetireCostCenterCommand(ActorContext actor, String id, String reason) {
    }

    // ---- BusinessPartner ----
    public record CreateBusinessPartnerCommand(ActorContext actor, String code, String name,
                                               String partnerType,
                                               Integer paymentTermDays,
                                               String paymentMethod,
                                               LocalDate effectiveFrom) {
    }

    public record UpdateBusinessPartnerCommand(ActorContext actor, String id, String name,
                                               String partnerType,
                                               Integer paymentTermDays,
                                               String paymentMethod,
                                               LocalDate effectiveFrom) {
    }

    public record RetireBusinessPartnerCommand(ActorContext actor, String id, String reason) {
    }
}
