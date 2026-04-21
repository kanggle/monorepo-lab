package com.example.shipping.application.service;

import com.example.web.exception.AccessDeniedException;
import com.example.shipping.application.exception.UnauthorizedShippingAccessException;
import com.example.shipping.application.result.ShippingResult;
import com.example.shipping.application.result.ShippingSummary;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.shipping.domain.model.*;
import com.example.shipping.domain.repository.ShippingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingQueryService 단위 테스트")
class ShippingQueryServiceTest {

    @InjectMocks
    private ShippingQueryService shippingQueryService;

    @Mock
    private ShippingRepository shippingRepository;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("주문 ID로 배송 조회 성공")
    void getShippingByOrderId_validOwner_returnsResult() {
        Shipping shipping = Shipping.create("order-1", "user-1", fixedClock);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipping));

        ShippingResult result = shippingQueryService.getShippingByOrderId("order-1", "user-1");

        assertThat(result.orderId()).isEqualTo("order-1");
        assertThat(result.status()).isEqualTo(ShippingStatus.PREPARING);
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID로 조회 시 예외")
    void getShippingByOrderId_notFound_throws() {
        given(shippingRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> shippingQueryService.getShippingByOrderId("order-x", "user-1"))
                .isInstanceOf(ShippingNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자의 배송 조회 시 접근 거부")
    void getShippingByOrderId_differentUser_throws() {
        Shipping shipping = Shipping.create("order-1", "user-1", fixedClock);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipping));

        assertThatThrownBy(() -> shippingQueryService.getShippingByOrderId("order-1", "user-other"))
                .isInstanceOf(UnauthorizedShippingAccessException.class);
    }

    @Test
    @DisplayName("배송 목록 조회 성공")
    void listShippings_noFilter_returnsList() {
        Shipping s1 = Shipping.create("order-1", "user-1", fixedClock);
        PageResult<Shipping> page = new PageResult<>(List.of(s1), 0, 20, 1, 1);
        PageQuery pageQuery = new PageQuery(0, 20, "createdAt", "DESC");
        given(shippingRepository.findAll(pageQuery)).willReturn(page);

        PageResult<ShippingSummary> result = shippingQueryService.listShippings("ADMIN", null, pageQuery);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("상태 필터로 배송 목록 조회")
    void listShippings_withStatusFilter_returnsList() {
        PageResult<Shipping> page = new PageResult<>(List.of(), 0, 20, 0, 0);
        PageQuery pageQuery = new PageQuery(0, 20, "createdAt", "DESC");
        given(shippingRepository.findByStatus(ShippingStatus.SHIPPED, pageQuery)).willReturn(page);

        PageResult<ShippingSummary> result = shippingQueryService.listShippings("ADMIN", ShippingStatus.SHIPPED, pageQuery);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("비관리자 역할로 배송 목록 조회 시 AccessDeniedException")
    void listShippings_nonAdminRole_throwsAccessDeniedException() {
        PageQuery pageQuery = new PageQuery(0, 20, "createdAt", "DESC");

        assertThatThrownBy(() -> shippingQueryService.listShippings("USER", null, pageQuery))
                .isInstanceOf(AccessDeniedException.class);
    }
}
