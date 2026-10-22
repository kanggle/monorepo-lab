package com.wms.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.web.idempotency.IdempotencyStore;
import com.wms.inventory.adapter.in.messaging.masterref.MasterEventParser;
import com.wms.inventory.adapter.in.web.controller.AdjustmentController;
import com.wms.inventory.adapter.in.web.controller.InventoryQueryController;
import com.wms.inventory.adapter.in.web.controller.MovementQueryController;
import com.wms.inventory.adapter.in.web.controller.ReservationController;
import com.wms.inventory.adapter.in.web.controller.TransferController;
import com.wms.inventory.application.port.in.AdjustStockUseCase;
import com.wms.inventory.application.port.in.ConfirmReservationUseCase;
import com.wms.inventory.application.port.in.MovementQueryUseCase;
import com.wms.inventory.application.port.in.QueryAdjustmentUseCase;
import com.wms.inventory.application.port.in.QueryInventoryUseCase;
import com.wms.inventory.application.port.in.QueryReservationUseCase;
import com.wms.inventory.application.port.in.QueryTransferUseCase;
import com.wms.inventory.application.port.in.ReceiveStockUseCase;
import com.wms.inventory.application.port.in.ReleaseReservationUseCase;
import com.wms.inventory.application.port.in.ReserveStockUseCase;
import com.wms.inventory.application.port.in.TransferStockUseCase;
import com.example.messaging.dedupe.EventDedupePort;
import com.wms.inventory.application.port.out.InventoryMovementRepository;
import com.wms.inventory.application.port.out.InventoryRepository;
import com.wms.inventory.application.port.out.LowStockAlertDebouncePort;
import com.wms.inventory.application.port.out.LowStockThresholdPort;
import com.wms.inventory.application.port.out.MasterReadModelPort;
import com.wms.inventory.application.port.out.MasterReadModelWriterPort;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.port.out.StockAdjustmentRepository;
import com.wms.inventory.application.port.out.StockTransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the full Spring context under the {@code standalone} profile to catch
 * bean wiring regressions that slice tests miss. No endpoints exist yet; this
 * test only asserts the bootstrap wiring is intact.
 *
 * <p>The {@code Master*Consumer} beans are profile-gated to {@code !standalone}
 * because the {@code standalone} profile excludes Kafka autoconfiguration —
 * a Testcontainers Kafka integration test verifies their behaviour separately.
 */
@SpringBootTest
@ActiveProfiles("standalone")
class ApplicationContextSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void infrastructureBeansAreWired() {
        assertThat(context.getBean(MasterEventParser.class)).isNotNull();
        assertThat(context.getBean(EventDedupePort.class)).isNotNull();
        assertThat(context.getBean(MasterReadModelPort.class)).isNotNull();
        assertThat(context.getBean(MasterReadModelWriterPort.class)).isNotNull();
        assertThat(context.getBean(IdempotencyStore.class)).isNotNull();
    }

    @Test
    void applicationPortsAreWired() {
        assertThat(context.getBean(ReceiveStockUseCase.class)).isNotNull();
        assertThat(context.getBean(QueryInventoryUseCase.class)).isNotNull();
        assertThat(context.getBean(MovementQueryUseCase.class)).isNotNull();
        assertThat(context.getBean(InventoryRepository.class)).isNotNull();
        assertThat(context.getBean(InventoryMovementRepository.class)).isNotNull();
        assertThat(context.getBean(OutboxWriter.class)).isNotNull();
    }

    @Test
    void reservationPortsAreWired() {
        assertThat(context.getBean(ReserveStockUseCase.class)).isNotNull();
        assertThat(context.getBean(ConfirmReservationUseCase.class)).isNotNull();
        assertThat(context.getBean(ReleaseReservationUseCase.class)).isNotNull();
        assertThat(context.getBean(QueryReservationUseCase.class)).isNotNull();
        assertThat(context.getBean(ReservationRepository.class)).isNotNull();
    }

    @Test
    void adjustmentTransferAndAlertPortsAreWired() {
        assertThat(context.getBean(AdjustStockUseCase.class)).isNotNull();
        assertThat(context.getBean(QueryAdjustmentUseCase.class)).isNotNull();
        assertThat(context.getBean(StockAdjustmentRepository.class)).isNotNull();
        assertThat(context.getBean(TransferStockUseCase.class)).isNotNull();
        assertThat(context.getBean(QueryTransferUseCase.class)).isNotNull();
        assertThat(context.getBean(StockTransferRepository.class)).isNotNull();
        assertThat(context.getBean(LowStockThresholdPort.class)).isNotNull();
        assertThat(context.getBean(LowStockAlertDebouncePort.class)).isNotNull();
    }

    /**
     * TASK-BE-647 — no test-side property override: the value must come from the
     * real base {@code application.yml}. Without the yml default the holder is
     * empty and low-stock detection is silently off after a restart.
     * BE-459 regression: a settings-changed update still overrides it.
     */
    @Test
    void lowStockDefaultThresholdResolvesToTenFromBaseConfig() {
        LowStockThresholdPort port = context.getBean(LowStockThresholdPort.class);
        java.util.UUID wh = java.util.UUID.randomUUID();
        java.util.UUID sku = java.util.UUID.randomUUID();
        assertThat(port.findThreshold(wh, sku)).contains(10);

        com.wms.inventory.application.port.out.LowStockThresholdWriterPort writer =
                context.getBean(com.wms.inventory.application.port.out.LowStockThresholdWriterPort.class);
        try {
            writer.updateDefaultThreshold(25);
            assertThat(port.findThreshold(wh, sku)).contains(25);
        } finally {
            writer.updateDefaultThreshold(10);
        }
    }

    @Test
    void webControllersAreRegistered() {
        assertThat(context.getBean(InventoryQueryController.class)).isNotNull();
        assertThat(context.getBean(MovementQueryController.class)).isNotNull();
        assertThat(context.getBean(ReservationController.class)).isNotNull();
        assertThat(context.getBean(AdjustmentController.class)).isNotNull();
        assertThat(context.getBean(TransferController.class)).isNotNull();
    }

    @Test
    void securityFilterChainIsPresent() {
        assertThat(context.getBean(SecurityFilterChain.class)).isNotNull();
    }
}
