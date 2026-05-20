package com.example.erp.masterdata.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class EmployeeRequests {

    private EmployeeRequests() {
    }

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 64) String employeeNumber,
            @NotBlank @Size(max = 256) String name,
            @NotBlank String departmentId,
            @NotBlank String costCenterId,
            @NotBlank String jobGradeId,
            LocalDate effectiveFrom) {
    }

    public record UpdateEmployeeRequest(
            @Size(max = 256) String name,
            String departmentId,
            String costCenterId,
            String jobGradeId,
            LocalDate effectiveFrom) {
    }

    public record RetireEmployeeRequest(
            @NotBlank @Size(max = 256) String reason) {
    }
}
