package com.example.erp.masterdata.domain.businesspartner;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.common.MasterStatusMachine;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(name = "business_partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessPartner {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "partner_type", length = 16, nullable = false)
    private PartnerType partnerType;

    @Embedded
    private PaymentTerms paymentTerms;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private MasterStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static BusinessPartner create(String id, String tenantId, String code, String name,
                                         PartnerType partnerType, PaymentTerms paymentTerms,
                                         EffectivePeriod period, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(partnerType, "partnerType");
        Objects.requireNonNull(paymentTerms, "paymentTerms");
        Objects.requireNonNull(period, "period");
        BusinessPartner b = new BusinessPartner();
        b.id = id;
        b.tenantId = tenantId;
        b.code = code;
        b.name = name;
        b.partnerType = partnerType;
        b.paymentTerms = paymentTerms;
        b.status = MasterStatus.ACTIVE;
        b.effectiveFrom = period.effectiveFrom();
        b.effectiveTo = period.effectiveTo();
        b.createdAt = now;
        b.updatedAt = now;
        return b;
    }

    public EffectivePeriod period() {
        return new EffectivePeriod(effectiveFrom, effectiveTo);
    }

    public void updateAttributes(String newName, PartnerType newPartnerType,
                                 PaymentTerms newPaymentTerms, Instant now) {
        if (newName != null) this.name = newName;
        if (newPartnerType != null) this.partnerType = newPartnerType;
        if (newPaymentTerms != null) this.paymentTerms = newPaymentTerms;
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        MasterStatusMachine.ensureRetireAllowed(this.status, "BusinessPartner " + id);
        this.status = MasterStatus.RETIRED;
        this.effectiveTo = now.atZone(ZoneOffset.UTC).toLocalDate();
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }
}
