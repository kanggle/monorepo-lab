package com.example.erp.masterdata.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class BusinessPartnerRequests {

    private BusinessPartnerRequests() {
    }

    public record PaymentTermsDto(
            @NotNull Integer termDays,
            @NotBlank String method) {
    }

    public record CreateBusinessPartnerRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 256) String name,
            @NotBlank String partnerType,
            @Valid @NotNull PaymentTermsDto paymentTerms,
            LocalDate effectiveFrom) {
    }

    public record UpdateBusinessPartnerRequest(
            @Size(max = 256) String name,
            String partnerType,
            @Valid PaymentTermsDto paymentTerms,
            LocalDate effectiveFrom) {
    }

    public record RetireBusinessPartnerRequest(
            @NotBlank @Size(max = 256) String reason) {
    }
}
