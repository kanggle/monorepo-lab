package com.example.settlement.application.service;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.InvalidCommissionRateException;
import com.example.settlement.domain.repository.CommissionRateRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import com.example.settlement.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommissionRateAdminServiceTest {

    @Mock
    private CommissionRateRepository rateRepository;
    @Mock
    private CommissionRateResolver rateResolver;
    @InjectMocks
    private CommissionRateAdminService service;

    @AfterEach
    void clear() {
        TenantContext.clear();
        SellerScopeContext.clear();
    }

    @Test
    void setRate_persists_override_and_returns_seller_source() {
        TenantContext.set("tenantA");

        CommissionRate rate = service.setRate("seller-1", 1200);

        verify(rateRepository).upsert("tenantA", "seller-1", 1200);
        assertThat(rate.rateBps()).isEqualTo(1200);
        assertThat(rate.source()).isEqualTo(CommissionRate.Source.SELLER_OVERRIDE);
    }

    @Test
    void setRate_rejects_out_of_range_with_422_exception() {
        TenantContext.set("tenantA");

        assertThatThrownBy(() -> service.setRate("seller-1", 10_001))
                .isInstanceOf(InvalidCommissionRateException.class);
        assertThatThrownBy(() -> service.setRate("seller-1", -1))
                .isInstanceOf(InvalidCommissionRateException.class);
        verify(rateRepository, never()).upsert(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void setRate_rejects_cross_seller_scope_as_404() {
        TenantContext.set("tenantA");
        SellerScopeContext.set("seller-1");

        assertThatThrownBy(() -> service.setRate("seller-2", 1000))
                .isInstanceOf(SellerScopeForbiddenException.class);
    }
}
