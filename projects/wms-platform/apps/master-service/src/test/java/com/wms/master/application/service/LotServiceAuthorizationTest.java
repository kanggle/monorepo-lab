package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateLotCommand;
import com.wms.master.application.command.DeactivateLotCommand;
import com.wms.master.application.command.ReactivateLotCommand;
import com.wms.master.application.command.UpdateLotCommand;
import com.wms.master.application.port.in.LotCrudUseCase;
import com.wms.master.application.port.in.LotQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.application.query.ListLotsQuery;
import com.wms.master.application.result.LotResult;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import com.example.common.page.PageQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Verifies method-level {@code @PreAuthorize} enforcement on {@link LotService}.
 * Mirrors {@link SkuServiceAuthorizationTest}; injection is by use-case interface
 * because {@code @PreAuthorize} generates a JDK dynamic proxy.
 *
 * <p>Role matrix being tested:
 * <ul>
 *   <li>create / update — MASTER_WRITE or MASTER_ADMIN allowed; MASTER_READ denied
 *   <li>deactivate / reactivate — MASTER_ADMIN only; MASTER_WRITE denied
 *   <li>findById / list — MASTER_READ+ (all master roles allowed)
 * </ul>
 */
@SpringJUnitConfig(LotServiceAuthorizationTest.TestConfig.class)
class LotServiceAuthorizationTest {

    @Autowired
    LotCrudUseCase crudUseCase;

    @Autowired
    LotQueryUseCase queryUseCase;

    @Autowired
    LotPersistencePort lotPersistencePort;

    @Autowired
    SkuPersistencePort skuPersistencePort;

    // ---------- create ----------

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void create_withReadOnlyRole_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void create_withWriteRole_isAllowed() {
        UUID skuId = UUID.randomUUID();
        Sku parentSku = Sku.create("SKU-AUTH", "Name", null, null,
                BaseUom.EA, TrackingType.LOT, null, null, null, 30, "actor");
        when(skuPersistencePort.findById(skuId)).thenReturn(Optional.of(parentSku));
        when(lotPersistencePort.insert(any(Lot.class))).thenAnswer(inv -> inv.getArgument(0));

        // Authorization gate passes — mock plumbing lets create succeed end-to-end.
        // Matches the happy-path style of SkuServiceAuthorizationTest
        // (TASK-BE-018 item 5 — replaces a bare try/catch(Exception ignored)).
        LotResult result = crudUseCase.create(sampleCreate(skuId));
        assertThat(result.lotNo()).isEqualTo("L-001");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void create_withAdminRole_isAllowed() {
        UUID skuId = UUID.randomUUID();
        Sku parentSku = Sku.create("SKU-ADMIN", "Name", null, null,
                BaseUom.EA, TrackingType.LOT, null, null, null, 30, "actor");
        when(skuPersistencePort.findById(skuId)).thenReturn(Optional.of(parentSku));
        when(lotPersistencePort.insert(any(Lot.class))).thenAnswer(inv -> inv.getArgument(0));

        LotResult result = crudUseCase.create(sampleCreate(skuId));
        assertThat(result.lotNo()).isEqualTo("L-001");
    }

    @Test
    @WithMockUser(authorities = "ROLE_SHIPPING_MANAGER")
    void create_withUnrelatedRole_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- update ----------

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void update_withReadOnlyRole_isDenied() {
        assertThatThrownBy(() -> crudUseCase.update(sampleUpdate(UUID.randomUUID())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void update_withWriteRole_passesAuthorizationGate() {
        UUID id = UUID.randomUUID();
        // Authorization gate passes — the call fails downstream (not-found / version),
        // but NOT with AccessDeniedException.
        assertThatThrownBy(() -> crudUseCase.update(sampleUpdate(id)))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    // ---------- deactivate ----------

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void deactivate_withWriteRole_isDenied_adminRequired() {
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateLotCommand(UUID.randomUUID(), "reason", 0L, "actor")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void deactivate_withAdminRole_passesAuthorizationGate() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateLotCommand(id, "reason", 0L, "actor")))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    // ---------- reactivate ----------

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void reactivate_withWriteRole_isDenied_adminRequired() {
        assertThatThrownBy(() -> crudUseCase.reactivate(
                new ReactivateLotCommand(UUID.randomUUID(), 0L, "actor")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void reactivate_withAdminRole_passesAuthorizationGate() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> crudUseCase.reactivate(
                new ReactivateLotCommand(id, 0L, "actor")))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    // ---------- read operations ----------

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void findById_withReadRole_isAllowed() {
        UUID id = UUID.randomUUID();
        Lot lot = Lot.create(UUID.randomUUID(), "L-001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, "actor");
        when(lotPersistencePort.findById(id)).thenReturn(Optional.of(lot));

        LotResult result = queryUseCase.findById(id);
        assertThat(result.lotNo()).isEqualTo("L-001");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void list_withReadRole_isAllowed() {
        when(lotPersistencePort.findPage(any(ListLotsCriteria.class), any(PageQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        PageResult<LotResult> result = queryUseCase.list(new ListLotsQuery(
                new ListLotsCriteria(null, null, null, null),
                new PageQuery(0, 20, "updatedAt", "desc")));
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void findById_withWriteRole_isAllowed() {
        UUID id = UUID.randomUUID();
        Lot lot = Lot.create(UUID.randomUUID(), "L-002",
                null, null, null, "actor");
        when(lotPersistencePort.findById(id)).thenReturn(Optional.of(lot));

        LotResult result = queryUseCase.findById(id);
        assertThat(result.lotNo()).isEqualTo("L-002");
    }

    @Test
    void unauthenticated_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate(UUID.randomUUID())))
                .isInstanceOfAny(AccessDeniedException.class,
                        AuthenticationCredentialsNotFoundException.class);
    }

    // ---------- helpers ----------

    private static CreateLotCommand sampleCreate(UUID skuId) {
        return new CreateLotCommand(skuId, "L-001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, "actor");
    }

    private static UpdateLotCommand sampleUpdate(UUID id) {
        return new UpdateLotCommand(id, LocalDate.of(2027, 1, 1), null, false,
                null, null, null, 0L, "actor");
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        LotPersistencePort lotPersistencePort() {
            return mock(LotPersistencePort.class);
        }

        @Bean
        SkuPersistencePort skuPersistencePort() {
            return mock(SkuPersistencePort.class);
        }

        @Bean
        DomainEventPort eventPort() {
            return mock(DomainEventPort.class);
        }

        @Bean
        LotExpirationBatchProcessor lotExpirationBatchProcessor() {
            return mock(LotExpirationBatchProcessor.class);
        }

        @Bean
        LotService lotService(LotPersistencePort lotPersistencePort,
                              SkuPersistencePort skuPersistencePort,
                              DomainEventPort eventPort,
                              LotExpirationBatchProcessor lotExpirationBatchProcessor) {
            return new LotService(lotPersistencePort, skuPersistencePort, eventPort,
                    lotExpirationBatchProcessor);
        }
    }
}
