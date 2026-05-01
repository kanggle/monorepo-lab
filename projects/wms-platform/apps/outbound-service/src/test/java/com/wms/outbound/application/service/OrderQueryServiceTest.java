package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.application.command.OrderQueryCommand;
import com.wms.outbound.application.port.in.QueryOrderUseCase.PageResult;
import com.wms.outbound.application.result.OrderResult;
import com.wms.outbound.application.service.fakes.FakeOrderPersistencePort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.exception.OrderNotFoundException;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.OrderLine;
import com.wms.outbound.domain.model.OrderSource;
import com.wms.outbound.domain.model.OrderStatus;
import com.wms.outbound.domain.model.OutboundSaga;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OrderQueryService} using port fakes only — no
 * Mockito, no Spring context. Covers the §1.2 findById happy path, the
 * §1.2 not-found branch, and the §1.3 list endpoint with the AC-03
 * "saga lookup is not invoked per row" assertion via
 * {@link FakeSagaPersistencePort#findByOrderIdCallCount}.
 */
class OrderQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    private FakeOrderPersistencePort orderPersistence;
    private FakeSagaPersistencePort sagaPersistence;
    private OrderQueryService service;

    @BeforeEach
    void setUp() {
        orderPersistence = new FakeOrderPersistencePort();
        sagaPersistence = new FakeSagaPersistencePort();
        service = new OrderQueryService(orderPersistence, sagaPersistence);
    }

    @Test
    void findById_found_returnsResultWithSagaState() {
        Order order = orderInState(OrderStatus.PICKING);
        orderPersistence.save(order);
        UUID sagaId = UUID.randomUUID();
        sagaPersistence.save(OutboundSaga.newRequested(sagaId, order.getId(), T0));

        OrderResult result = service.findById(order.getId());

        assertThat(result.orderId()).isEqualTo(order.getId());
        assertThat(result.status()).isEqualTo("PICKING");
        assertThat(result.sagaId()).isEqualTo(sagaId);
        assertThat(result.sagaState()).isEqualTo("REQUESTED");
    }

    @Test
    void findById_notFound_raisesOrderNotFound() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.findById(missing))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrders_doesNotCallSagaPerRow() {
        // Three orders + matching sagas — list() must enrich them via the
        // bulk findSagaStatesByOrderIds path, NOT via per-row findByOrderId.
        for (int i = 0; i < 3; i++) {
            Order order = orderInState(OrderStatus.PICKING);
            orderPersistence.save(order);
            UUID sagaId = UUID.randomUUID();
            sagaPersistence.save(OutboundSaga.newRequested(sagaId, order.getId(), T0));
        }

        OrderQueryCommand cmd = new OrderQueryCommand(
                null, null, null, null, null, null, null, null, null, 0, 20);
        PageResult page = service.list(cmd);

        assertThat(page.items()).hasSize(3);
        assertThat(page.total()).isEqualTo(3L);
        // Every row carries its enriched saga state, populated via the bulk
        // lookup (REQUESTED for newly-created sagas).
        assertThat(page.items())
                .allMatch(r -> "REQUESTED".equals(r.sagaState()));
        // CRITICAL AC-03 assertion: the per-row N+1 path must remain unused.
        assertThat(sagaPersistence.findByOrderIdCallCount).isZero();
    }

    private static Order orderInState(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        OrderLine line = new OrderLine(UUID.randomUUID(), orderId, 1,
                UUID.randomUUID(), null, 5);
        return new Order(orderId, "ORD-" + orderId, OrderSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID(),
                null, null, status, 0L,
                T0, "creator", T0, "creator",
                List.of(line));
    }
}
