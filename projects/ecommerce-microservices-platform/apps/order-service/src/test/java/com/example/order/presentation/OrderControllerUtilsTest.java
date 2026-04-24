package com.example.order.presentation;

import com.example.common.page.PageQuery;
import com.example.order.domain.model.OrderStatus;
import com.example.order.presentation.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderControllerUtils 단위 테스트")
class OrderControllerUtilsTest {

    // ── buildPageQuery ────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상적인 page와 size가 주어지면 그대로 반영되고 sortBy=createdAt, sortDirection=DESC로 반환한다")
    void buildPageQuery_normalPageAndSize_returnAsIsWithCreatedAtDesc() {
        PageQuery result = OrderControllerUtils.buildPageQuery(2, 10, null);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.sortBy()).isEqualTo("createdAt");
        assertThat(result.sortDirection()).isEqualTo("DESC");
    }

    @Test
    @DisplayName("page가 -1이면 0으로 보정된다")
    void buildPageQuery_negativePageMinusOne_clampedToZero() {
        PageQuery result = OrderControllerUtils.buildPageQuery(-1, 10, null);

        assertThat(result.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("size가 0이면 기본값 20으로 대체된다")
    void buildPageQuery_sizeZero_defaultsToTwenty() {
        PageQuery result = OrderControllerUtils.buildPageQuery(0, 0, null);

        assertThat(result.size()).isEqualTo(OrderControllerUtils.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("size가 -1이면 기본값 20으로 대체된다")
    void buildPageQuery_sizeNegative_defaultsToTwenty() {
        PageQuery result = OrderControllerUtils.buildPageQuery(0, -1, null);

        assertThat(result.size()).isEqualTo(OrderControllerUtils.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("size가 최대값 100이면 그대로 100으로 반환된다")
    void buildPageQuery_sizeAtMax_returnsHundred() {
        PageQuery result = OrderControllerUtils.buildPageQuery(0, 100, null);

        assertThat(result.size()).isEqualTo(OrderControllerUtils.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("size가 최대값 100을 초과한 101이면 100으로 잘린다")
    void buildPageQuery_sizeExceedsMax_clampedToHundred() {
        PageQuery result = OrderControllerUtils.buildPageQuery(0, 101, null);

        assertThat(result.size()).isEqualTo(OrderControllerUtils.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("size가 50이면 정상 범위로 그대로 50으로 반환된다")
    void buildPageQuery_sizeNormalRange_returnsFifty() {
        PageQuery result = OrderControllerUtils.buildPageQuery(0, 50, null);

        assertThat(result.size()).isEqualTo(50);
    }

    // ── parseStatus ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("status가 null이면 null을 반환한다")
    void parseStatus_nullStatus_returnsNull() {
        OrderStatus result = OrderControllerUtils.parseStatus(null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("status가 빈 문자열이면 null을 반환한다")
    void parseStatus_emptyString_returnsNull() {
        OrderStatus result = OrderControllerUtils.parseStatus("");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("status가 공백 문자열이면 null을 반환한다")
    void parseStatus_blankString_returnsNull() {
        OrderStatus result = OrderControllerUtils.parseStatus("  ");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("status가 'PENDING'이면 OrderStatus.PENDING을 반환한다")
    void parseStatus_pending_returnsOrderStatusPending() {
        OrderStatus result = OrderControllerUtils.parseStatus("PENDING");

        assertThat(result).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("status가 'CONFIRMED'이면 OrderStatus.CONFIRMED를 반환한다")
    void parseStatus_confirmed_returnsOrderStatusConfirmed() {
        OrderStatus result = OrderControllerUtils.parseStatus("CONFIRMED");

        assertThat(result).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("유효하지 않은 status 값이 주어지면 InvalidOrderStatusException이 발생한다")
    void parseStatus_invalidStatus_throwsInvalidOrderStatusException() {
        assertThatThrownBy(() -> OrderControllerUtils.parseStatus("INVALID_STATUS"))
                .isInstanceOf(InvalidOrderStatusException.class);
    }
}
