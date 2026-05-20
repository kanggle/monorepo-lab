package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.businesspartner.BusinessPartner;
import com.example.erp.masterdata.domain.businesspartner.PaymentTerms;

import java.time.Instant;
import java.time.LocalDate;

public record BusinessPartnerView(String id, String code, String name, String partnerType,
                                   PaymentTermsDto paymentTerms, String status,
                                   LocalDate effectiveFrom, LocalDate effectiveTo,
                                   Instant createdAt, Instant updatedAt) {

    public record PaymentTermsDto(Integer termDays, String method) {
        public static PaymentTermsDto from(PaymentTerms terms) {
            if (terms == null) return null;
            return new PaymentTermsDto(terms.getTermDays(),
                    terms.getMethod() == null ? null : terms.getMethod().name());
        }
    }

    public static BusinessPartnerView from(BusinessPartner b) {
        return new BusinessPartnerView(b.getId(), b.getCode(), b.getName(),
                b.getPartnerType().name(),
                PaymentTermsDto.from(b.getPaymentTerms()),
                b.getStatus().name(), b.getEffectiveFrom(), b.getEffectiveTo(),
                b.getCreatedAt(), b.getUpdatedAt());
    }
}
