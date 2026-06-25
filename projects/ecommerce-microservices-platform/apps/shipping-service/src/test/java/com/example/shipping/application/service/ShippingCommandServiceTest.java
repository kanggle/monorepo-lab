package com.example.shipping.application.service;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.command.UpdateShippingStatusCommand;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.shipping.domain.exception.InvalidStatusTransitionException;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingCommandService 단위 테스트")
class ShippingCommandServiceTest {

    private ShippingCommandService shippingCommandService;

    @Mock
    private ShippingRepository shippingRepository;

    @Mock
    private ShippingEventPublisher shippingEventPublisher;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        shippingCommandService = new ShippingCommandService(shippingRepository, shippingEventPublisher, fixedClock);
    }

    @Test
    @DisplayName("배송 생성 성공")
    void createShipping_validCommand_success() {
        given(shippingRepository.existsByOrderId("order-1")).willReturn(false);
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        shippingCommandService.createShipping(new CreateShippingCommand("tenant-a", "order-1", "user-1"));

        verify(shippingRepository).save(any(Shipping.class));
    }

    @Test
    @DisplayName("중복 orderId로 배송 생성 시 멱등 처리 (무시)")
    void createShipping_duplicateOrderId_skips() {
        given(shippingRepository.existsByOrderId("order-1")).willReturn(true);

        shippingCommandService.createShipping(new CreateShippingCommand("tenant-a", "order-1", "user-1"));

        verify(shippingRepository, never()).save(any());
    }

    @Test
    @DisplayName("배송 상태 업데이트 성공 및 이벤트 발행")
    void updateStatus_validTransition_updatesAndPublishesEvent() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", false, "ADMIN");

        UpdateShippingStatusResult result = shippingCommandService.updateStatus(command);

        assertThat(result.status()).isEqualTo(ShippingStatus.SHIPPED);
        verify(shippingEventPublisher).publishShippingStatusChanged(
                eq("tenant-a"), eq(shipping.getShippingId()), eq("order-1"), eq("user-1"),
                eq(ShippingStatus.PREPARING), eq(ShippingStatus.SHIPPED),
                eq("TRK-001"), eq("CJ대한통운"));
        verify(shippingEventPublisher, never()).publishManualShipConfirmRequested(any(), any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 배송 ID로 상태 업데이트 시 예외")
    void updateStatus_shippingNotFound_throws() {
        given(shippingRepository.findByIdForTenant("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> shippingCommandService.updateStatus(
                new UpdateShippingStatusCommand("nonexistent", ShippingStatus.SHIPPED, "TRK", "CJ", false, "ADMIN")))
                .isInstanceOf(ShippingNotFoundException.class);
    }

    @Test
    @DisplayName("잘못된 전이 시도 시 예외")
    void updateStatus_invalidTransition_throws() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));

        assertThatThrownBy(() -> shippingCommandService.updateStatus(
                new UpdateShippingStatusCommand(shipping.getShippingId(), ShippingStatus.DELIVERED, null, null, false, "ADMIN")))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("비관리자 역할로 상태 업데이트 시 AccessDeniedException")
    void updateStatus_nonAdminRole_throwsAccessDeniedException() {
        assertThatThrownBy(() -> shippingCommandService.updateStatus(
                new UpdateShippingStatusCommand("ship-1", ShippingStatus.SHIPPED, "TRK", "CJ", false, "USER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("markShippedByOrderId: orderNo로 조회해 PREPARING -> SHIPPED 전이 + 이벤트 발행")
    void markShippedByOrderId_preparing_transitionsToShipped() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        shippingCommandService.markShippedByOrderId("order-1", "SHP-001", "CJ-LOGISTICS");

        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getTrackingNumber()).isEqualTo("SHP-001");
        assertThat(shipping.getCarrier()).isEqualTo("CJ-LOGISTICS");
        verify(shippingEventPublisher).publishShippingStatusChanged(
                eq("tenant-a"), eq(shipping.getShippingId()), eq("order-1"), eq("user-1"),
                eq(ShippingStatus.PREPARING), eq(ShippingStatus.SHIPPED),
                eq("SHP-001"), eq("CJ-LOGISTICS"));
    }

    @Test
    @DisplayName("markShippedByOrderId: 이미 SHIPPED면 멱등 처리 (no-op)")
    void markShippedByOrderId_alreadyShipped_idempotent() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        shipping.transitionTo(ShippingStatus.SHIPPED, "OLD-TRK", "OLD-CARRIER", fixedClock);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipping));

        shippingCommandService.markShippedByOrderId("order-1", "SHP-002", "HANJIN");

        assertThat(shipping.getTrackingNumber()).isEqualTo("OLD-TRK");
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never()).publishShippingStatusChanged(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("markShippedByOrderId: 미존재 주문이면 ShippingNotFoundException")
    void markShippedByOrderId_notFound_throws() {
        given(shippingRepository.findByOrderId("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> shippingCommandService.markShippedByOrderId("nope", "SHP", "CJ"))
                .isInstanceOf(ShippingNotFoundException.class);
    }

    // ─── Multi-value X-User-Role (BE-393) ─────────────────────────────────

    @Test
    @DisplayName("멀티롤 헤더에 ADMIN 포함 시 상태 업데이트 허용 (multi-domain operator)")
    void updateStatus_multiRoleContainingAdmin_admitted() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운",
                false, "ADMIN,ERP_OPERATOR,SCM_OPERATOR");

        assertThatCode(() -> shippingCommandService.updateStatus(command))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("단일 ADMIN 롤 헤더는 계속 허용 (회귀 방지)")
    void updateStatus_singleAdminRole_admitted() {
        Shipping shipping = Shipping.create("tenant-a", "order-2", "user-1", fixedClock);
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.SHIPPED, "TRK-002", "CJ대한통운", false, "ADMIN");

        assertThatCode(() -> shippingCommandService.updateStatus(command))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN 없는 멀티롤은 거부")
    void updateStatus_multiRoleWithoutAdmin_throwsAccessDeniedException() {
        assertThatThrownBy(() -> shippingCommandService.updateStatus(
                new UpdateShippingStatusCommand("ship-1", ShippingStatus.SHIPPED, "TRK", "CJ",
                        false, "SCM_OPERATOR,ERP_OPERATOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("null 헤더는 거부")
    void updateStatus_nullRole_throwsAccessDeniedException() {
        assertThatThrownBy(() -> shippingCommandService.updateStatus(
                new UpdateShippingStatusCommand("ship-1", ShippingStatus.SHIPPED, "TRK", "CJ", false, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SUPERADMIN 서브스트링만 있는 헤더는 거부 (부분일치 방지)")
    void updateStatus_superadminSubstringOnly_throwsAccessDeniedException() {
        assertThatThrownBy(() -> shippingCommandService.updateStatus(
                new UpdateShippingStatusCommand("ship-1", ShippingStatus.SHIPPED, "TRK", "CJ", false, "SUPERADMIN")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── Manual ship-confirm wms deduction (TASK-MONO-305, ADR-MONO-022 D4 v2(c)) ──

    private Shipping wmsRoutedPreparingShipping() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        shipping.markWmsRouted();
        return shipping;
    }

    @Test
    @DisplayName("SHIPPED + deductWmsInventory + wmsRouted 조합에서만 manual-confirm 발행")
    void updateStatus_shippedDeductAndRouted_publishesManualConfirm() {
        Shipping shipping = wmsRoutedPreparingShipping();
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", true, "ADMIN");

        shippingCommandService.updateStatus(command);

        verify(shippingEventPublisher).publishManualShipConfirmRequested(
                eq("tenant-a"), eq("order-1"), eq("CJ대한통운"), eq("TRK-001"));
    }

    @Test
    @DisplayName("deductWmsInventory=false 면 wmsRouted 라도 manual-confirm 미발행")
    void updateStatus_deductOff_doesNotPublishManualConfirm() {
        Shipping shipping = wmsRoutedPreparingShipping();
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", false, "ADMIN");

        shippingCommandService.updateStatus(command);

        verify(shippingEventPublisher, never()).publishManualShipConfirmRequested(any(), any(), any(), any());
    }

    @Test
    @DisplayName("wmsRouted=false 면 deductWmsInventory=true 라도 manual-confirm 미발행")
    void updateStatus_notRouted_doesNotPublishManualConfirm() {
        // create() leaves wmsRouted=false.
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", true, "ADMIN");

        shippingCommandService.updateStatus(command);

        verify(shippingEventPublisher, never()).publishManualShipConfirmRequested(any(), any(), any(), any());
    }

    @Test
    @DisplayName("SHIPPED 가 아닌 전이(IN_TRANSIT)면 deduct+routed 라도 manual-confirm 미발행")
    void updateStatus_nonShippedTarget_doesNotPublishManualConfirm() {
        Shipping shipping = wmsRoutedPreparingShipping();
        shipping.transitionTo(ShippingStatus.SHIPPED, "TRK-001", "CJ대한통운", fixedClock);
        given(shippingRepository.findByIdForTenant(shipping.getShippingId())).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusCommand command = new UpdateShippingStatusCommand(
                shipping.getShippingId(), ShippingStatus.IN_TRANSIT, null, null, true, "ADMIN");

        shippingCommandService.updateStatus(command);

        verify(shippingEventPublisher, never()).publishManualShipConfirmRequested(any(), any(), any(), any());
    }

    @Test
    @DisplayName("markShippingWmsRouted: 조회된 행을 wmsRouted=true 로 저장")
    void markShippingWmsRouted_existingRow_marksAndSaves() {
        Shipping shipping = Shipping.create("tenant-a", "order-1", "user-1", fixedClock);
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipping));
        given(shippingRepository.save(any(Shipping.class))).willAnswer(inv -> inv.getArgument(0));

        shippingCommandService.markShippingWmsRouted("order-1");

        assertThat(shipping.isWmsRouted()).isTrue();
        verify(shippingRepository).save(shipping);
    }

    @Test
    @DisplayName("markShippingWmsRouted: 이미 routed 면 저장하지 않음 (멱등)")
    void markShippingWmsRouted_alreadyRouted_idempotentNoSave() {
        Shipping shipping = wmsRoutedPreparingShipping();
        given(shippingRepository.findByOrderId("order-1")).willReturn(Optional.of(shipping));

        shippingCommandService.markShippingWmsRouted("order-1");

        verify(shippingRepository, never()).save(any());
    }

    @Test
    @DisplayName("markShippingWmsRouted: 미존재 주문이면 no-op (저장 안 함)")
    void markShippingWmsRouted_missingRow_noOp() {
        given(shippingRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        shippingCommandService.markShippingWmsRouted("order-x");

        verify(shippingRepository, never()).save(any());
    }
}
