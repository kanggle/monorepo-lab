package com.example.erp.masterdata.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class DepartmentRequests {

    private DepartmentRequests() {
    }

    public record CreateDepartmentRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 256) String name,
            String parentId,
            LocalDate effectiveFrom) {
    }

    public record UpdateDepartmentRequest(
            @Size(max = 256) String name,
            LocalDate effectiveFrom) {
    }

    public record RetireDepartmentRequest(
            @NotBlank @Size(max = 256) String reason) {
    }

    public record MoveParentRequest(
            String newParentId,
            LocalDate effectiveFrom,
            @Size(max = 256) String reason) {
    }
}
