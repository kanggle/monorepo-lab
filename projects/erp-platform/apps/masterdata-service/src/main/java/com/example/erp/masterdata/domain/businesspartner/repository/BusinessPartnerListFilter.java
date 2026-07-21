package com.example.erp.masterdata.domain.businesspartner.repository;

import com.example.erp.masterdata.domain.businesspartner.PartnerType;

import java.time.LocalDate;

/**
 * Optional list-query filter for BusinessPartner (masterdata-api.md § GET
 * /business-partners — {@code ?asOf=&active=&partnerType=}). Every field
 * nullable; {@code null} = do not constrain on that dimension.
 *
 * <p>Pure Java (domain boundary rule).
 */
public record BusinessPartnerListFilter(LocalDate asOf, Boolean active, PartnerType partnerType) {

    public static BusinessPartnerListFilter unfiltered() {
        return new BusinessPartnerListFilter(null, null, null);
    }
}
