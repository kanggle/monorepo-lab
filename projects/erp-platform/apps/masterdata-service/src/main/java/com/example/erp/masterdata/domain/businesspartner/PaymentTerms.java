package com.example.erp.masterdata.domain.businesspartner;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Embedded VO carrying payment terms (term days + method). Pure-ish (JPA
 * @Embeddable is the domain↔framework allowed exception).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PaymentTerms {

    private Integer termDays;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private PaymentMethod method;

    public static PaymentTerms of(int termDays, PaymentMethod method) {
        return new PaymentTerms(termDays, method);
    }

    public enum PaymentMethod {
        BANK_TRANSFER,
        CREDIT_CARD,
        CASH,
        CHECK
    }
}
