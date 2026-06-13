package com.example.settlement.application.service;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.SellerBalance;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    @Mock
    private CommissionAccrualRepository accrualRepository;
    @InjectMocks
    private SettlementQueryService service;

    private static final PageQuery PAGE = PageQuery.of(0, 20, "occurredAt", "DESC");

    @AfterEach
    void clear() {
        SellerScopeContext.clear();
    }

    @Test
    void listAccruals_passes_filters_through_when_unrestricted() {
        when(accrualRepository.findAccruals(eq("seller-1"), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        service.listAccruals("seller-1", null, PAGE);

        verify(accrualRepository).findAccruals(eq("seller-1"), isNull(), any());
    }

    @Test
    void listAccruals_blank_filters_become_null() {
        when(accrualRepository.findAccruals(isNull(), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        service.listAccruals("  ", "", PAGE);

        verify(accrualRepository).findAccruals(isNull(), isNull(), any());
    }

    @Test
    void listAccruals_rejects_seller_filter_outside_bound_scope_as_404() {
        SellerScopeContext.set("seller-1");

        assertThatThrownBy(() -> service.listAccruals("seller-2", null, PAGE))
                .isInstanceOf(SellerScopeForbiddenException.class);
        verify(accrualRepository, never()).findAccruals(any(), any(), any());
    }

    @Test
    void listAccruals_allows_own_seller_filter_when_restricted() {
        SellerScopeContext.set("seller-1");
        when(accrualRepository.findAccruals(eq("seller-1"), isNull(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        service.listAccruals("seller-1", null, PAGE);

        verify(accrualRepository).findAccruals(eq("seller-1"), isNull(), any());
    }

    @Test
    void sellerBalance_rejects_cross_seller_scope_as_404() {
        SellerScopeContext.set("seller-1");

        assertThatThrownBy(() -> service.sellerBalance("seller-2"))
                .isInstanceOf(SellerScopeForbiddenException.class);
    }

    @Test
    void sellerBalance_returns_aggregate() {
        when(accrualRepository.sellerBalance("seller-1"))
                .thenReturn(new SellerBalance("seller-1", 27_000L, 3_000L, 30_000L, 1L));

        SellerBalance balance = service.sellerBalance("seller-1");

        assertThat(balance.accruedNetMinor()).isEqualTo(27_000L);
        assertThat(balance.platformCommissionMinor()).isEqualTo(3_000L);
    }
}
