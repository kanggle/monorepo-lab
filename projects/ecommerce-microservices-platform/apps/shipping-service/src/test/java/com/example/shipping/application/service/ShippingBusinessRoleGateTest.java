package com.example.shipping.application.service;

import com.example.common.page.PageQuery;
import com.example.shipping.application.command.UpdateShippingStatusCommand;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.web.exception.AccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · owner decisions 2026-10-16 UTC) — shipping-service re-checks the E-commerce
 * business role on its four operator operations (list · summary · status change · tracking refresh on
 * {@code /api/shippings/**}). The buyer's own read ({@code GET /api/shippings/orders/{orderId}}) and the carrier
 * webhook are consumer / public paths and are NOT part of this gate.
 *
 * <p>Decision (ecommerce iam-integration.md § 업무 역할): reads = {@code ECOMMERCE_SHIPPING_READ ∨
 * ECOMMERCE_SHIPPING_WRITE}; status change · tracking refresh = {@code ECOMMERCE_SHIPPING_WRITE}. «Allowed» =
 * anything but {@link AccessDeniedException} (the repositories are mocks).
 */
@DisplayName("TASK-MONO-795 shipping 업무 역할 게이트 (운영자 연산 4 전수)")
class ShippingBusinessRoleGateTest {

    private static final String ENTRY = "ECOMMERCE_OPERATOR";

    private record Op(String name, boolean write, Consumer<String> call) {}

    private static List<Op> ops() {
        ShippingRepository repo = mock(ShippingRepository.class);
        ShippingQueryService query = new ShippingQueryService(repo);
        ShippingCommandService command = new ShippingCommandService(repo, mock(ShippingEventPublisher.class),
                Clock.systemUTC());
        RefreshTrackingService refresh = new RefreshTrackingService(repo, mock(CarrierAdvanceProcessor.class));
        return List.of(
                new Op("list", false, role -> query.listShippings(role, null, new PageQuery(0, 20, null, null))),
                new Op("summary", false, query::getPeriodSummary),
                new Op("status", true, role -> command.updateStatus(new UpdateShippingStatusCommand(
                        "s-1", ShippingStatus.SHIPPED, "T-1", "CJ", false, role))),
                new Op("refresh-tracking", true, role -> refresh.refreshFromCarrier("s-1", role)));
    }

    static Stream<Arguments> personas() {
        return Stream.of(
                Arguments.of("shipping read set", ENTRY + ",ECOMMERCE_SHIPPING_READ", true, false),
                Arguments.of("shipping operate set", ENTRY + ",ECOMMERCE_SHIPPING_READ,ECOMMERCE_SHIPPING_WRITE", true, true),
                Arguments.of("write role alone still reads", ENTRY + ",ECOMMERCE_SHIPPING_WRITE", true, true),
                Arguments.of("🔴 order operate set only (another area)", ENTRY + ",ECOMMERCE_ORDER_READ,ECOMMERCE_ORDER_WRITE", false, false),
                Arguments.of("🔴 entry role alone", ENTRY, false, false),
                Arguments.of("CUSTOMER", "CUSTOMER", false, false),
                Arguments.of("blank", "", false, false),
                Arguments.of("absent (null)", null, false, false),
                Arguments.of("lower-case role (comparison ignores case)", "ecommerce_shipping_read", true, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("personas")
    @DisplayName("each persona × the four shipping operator operations")
    void gate(String persona, String roleHeader, boolean mayRead, boolean mayWrite) {
        List<String> wrong = new ArrayList<>();
        for (Op op : ops()) {
            boolean denied;
            try {
                op.call().accept(roleHeader);
                denied = false;
            } catch (AccessDeniedException e) {
                denied = true;
            } catch (RuntimeException other) {
                denied = false; // past the gate — the mock repository answered something the code did not like
            }
            boolean expectAllowed = op.write() ? mayWrite : mayRead;
            if (expectAllowed == denied) {
                wrong.add(op.name() + " → " + (denied ? "denied" : "allowed"));
            }
        }
        assertThat(wrong).as(persona).isEmpty();
    }
}
