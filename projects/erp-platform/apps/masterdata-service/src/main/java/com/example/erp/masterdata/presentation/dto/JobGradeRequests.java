package com.example.erp.masterdata.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class JobGradeRequests {

    private JobGradeRequests() {
    }

    public record CreateJobGradeRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 256) String name,
            @NotNull Integer displayOrder,
            LocalDate effectiveFrom) {
    }

    public record UpdateJobGradeRequest(
            @Size(max = 256) String name,
            Integer displayOrder,
            LocalDate effectiveFrom) {
    }

    public record RetireJobGradeRequest(
            @NotBlank @Size(max = 256) String reason) {
    }
}
