package com.example.product.application.service;

import com.example.product.application.dto.SellerListResult;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.port.SellerQueryPort;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.Seller;
import com.example.product.domain.repository.SellerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SellerQueryService 단위 테스트 (list + detail, tenant-scoped delegation)")
class SellerQueryServiceTest {

    @Mock
    private SellerQueryPort sellerQueryPort;

    @Mock
    private SellerRepository sellerRepository;

    @InjectMocks
    private SellerQueryService sellerQueryService;

    @Test
    @DisplayName("listSellers - 포트의 paged 결과를 그대로 위임 반환한다")
    void listSellers_delegatesToPort() {
        SellerListResult result = new SellerListResult(
                List.of(new SellerSummary("seller-a1", "셀러 A1",
                        com.example.product.domain.model.SellerStatus.ACTIVE, null, null)),
                0, 20, 1L);
        given(sellerQueryPort.findAll(0, 20)).willReturn(result);

        SellerListResult actual = sellerQueryService.listSellers(0, 20);

        assertThat(actual).isSameAs(result);
    }

    @Test
    @DisplayName("getSeller - 존재하는 셀러는 SellerSummary 로 반환한다")
    void getSeller_present_returnsSummary() {
        Seller seller = Seller.register("seller-a1", "셀러 A1");
        given(sellerRepository.findById("seller-a1")).willReturn(Optional.of(seller));

        SellerSummary actual = sellerQueryService.getSeller("seller-a1");

        assertThat(actual.sellerId()).isEqualTo("seller-a1");
        assertThat(actual.displayName()).isEqualTo("셀러 A1");
    }

    @Test
    @DisplayName("getSeller - 부재/cross-tenant 셀러는 SellerNotFoundException")
    void getSeller_absent_throwsNotFound() {
        given(sellerRepository.findById("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerQueryService.getSeller("ghost"))
                .isInstanceOf(SellerNotFoundException.class);
    }
}
