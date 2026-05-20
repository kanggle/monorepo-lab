package com.example.erp.masterdata.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class CostCenterRequests {

    private CostCenterRequests() {
    }

    public record CreateCostCenterRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 256) String name,
            @NotBlank String departmentId,
            LocalDate effectiveFrom) {
    }

    public record UpdateCostCenterRequest(
            @Size(max = 256) String name,
            String departmentId,
            LocalDate effectiveFrom) {
    }

    public record RetireCostCenterRequest(
            @NotBlank @Size(max = 256) String reason) {
    }
}
