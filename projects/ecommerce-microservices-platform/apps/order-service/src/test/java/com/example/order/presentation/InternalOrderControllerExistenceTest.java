package com.example.order.presentation;

import com.example.order.application.service.OperatorOrderCancellationService;
import com.example.order.application.service.OrderExistenceQueryService;
import com.example.order.application.service.StalePaidOrderConfirmService;
import com.example.order.presentation.dto.OrderExistenceRequest;
import com.example.order.presentation.dto.OrderExistenceResponse;
import com.example.order.presentation.exception.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalOrderController — 주문 존재 조회 (TASK-INT-028)")
class InternalOrderControllerExistenceTest {

    @Mock
    private StalePaidOrderConfirmService stalePaidOrderConfirmService;
    @Mock
    private OperatorOrderCancellationService operatorOrderCancellationService;
    @Mock
    private OrderExistenceQueryService orderExistenceQueryService;

    private InternalOrderController controller() {
        return new InternalOrderController(
                stalePaidOrderConfirmService, operatorOrderCancellationService, orderExistenceQueryService);
    }

    @Test
    @DisplayName("존재하는 id 만 돌려준다")
    void returnsTheExistingSubset() {
        given(orderExistenceQueryService.existing(List.of("o-1", "o-2"))).willReturn(Set.of("o-1"));

        ResponseEntity<OrderExistenceResponse> response =
                controller().existence(new OrderExistenceRequest(List.of("o-1", "o-2")));

        assertThat(response.getBody().existingOrderIds()).containsExactly("o-1");
    }

    @Test
    @DisplayName("본문 없음·빈 목록은 400 — 부분 답을 만들지 않는다")
    void missingOrEmptyOrderIds_isRejected() {
        assertThatThrownBy(() -> controller().existence(null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> controller().existence(new OrderExistenceRequest(null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> controller().existence(new OrderExistenceRequest(Collections.emptyList())))
                .isInstanceOf(InvalidRequestException.class);
        verify(orderExistenceQueryService, never()).existing(any());
    }

    @Test
    @DisplayName("501개는 400, 빈 id 가 섞이면 400")
    void tooManyOrBlankIds_isRejected() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= OrderExistenceRequest.MAX_ORDER_IDS; i++) {
            tooMany.add("o-" + i);
        }
        assertThatThrownBy(() -> controller().existence(new OrderExistenceRequest(tooMany)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> controller().existence(new OrderExistenceRequest(Arrays.asList("o-1", " "))))
                .isInstanceOf(InvalidRequestException.class);
        verify(orderExistenceQueryService, never()).existing(any());
    }
}
