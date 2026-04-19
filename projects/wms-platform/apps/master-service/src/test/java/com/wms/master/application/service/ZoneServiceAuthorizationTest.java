package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wms.master.application.command.CreateZoneCommand;
import com.wms.master.application.command.DeactivateZoneCommand;
import com.wms.master.application.port.in.ZoneCrudUseCase;
import com.wms.master.application.port.in.ZoneQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.Zone;
import com.wms.master.domain.model.ZoneType;
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
 * Verifies method-level {@code @PreAuthorize} enforcement on {@link ZoneService}.
 * Mirrors {@link WarehouseServiceAuthorizationTest}; injection is by use-case
 * interface (not the concrete {@code ZoneService} class) because {@code @PreAuthorize}
 * generates a JDK dynamic proxy.
 */
@SpringJUnitConfig(ZoneServiceAuthorizationTest.TestConfig.class)
class ZoneServiceAuthorizationTest {

    @Autowired
    ZoneCrudUseCase crudUseCase;

    @Autowired
    ZoneQueryUseCase queryUseCase;

    @Autowired
    ZonePersistencePort zonePersistencePort;

    @Autowired
    WarehousePersistencePort warehousePersistencePort;

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void create_withReadOnlyRole_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void create_withWriteRole_isAllowed() {
        Warehouse activeParent = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "seed");
        when(warehousePersistencePort.findById(WAREHOUSE_ID)).thenReturn(Optional.of(activeParent));
        when(zonePersistencePort.insert(any(Zone.class))).thenAnswer(inv -> inv.getArgument(0));

        ZoneResult result = crudUseCase.create(sampleCreate());
        assertThat(result.zoneCode()).isEqualTo("Z-A");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void deactivate_withWriteRole_isDenied_adminRequired() {
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateZoneCommand(UUID.randomUUID(), "closing", 0L, "actor")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void deactivate_withAdminRole_isAllowed() {
        UUID id = UUID.randomUUID();
        Zone zone = Zone.create(WAREHOUSE_ID, "Z-A", "Name", ZoneType.AMBIENT, "actor");
        when(zonePersistencePort.findById(id)).thenReturn(Optional.of(zone));
        when(zonePersistencePort.update(any(Zone.class))).thenAnswer(inv -> inv.getArgument(0));

        // We only care that the @PreAuthorize lets us through — the domain
        // logic layer beyond (version mismatch) is exercised elsewhere.
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateZoneCommand(id, "closing", 5L, "actor")))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void list_withReadRole_isAllowed() {
        Warehouse parent = Warehouse.create("WH01", "Seoul", null, "Asia/Seoul", "seed");
        when(warehousePersistencePort.findById(WAREHOUSE_ID)).thenReturn(Optional.of(parent));
        when(zonePersistencePort.findPage(any(), any())).thenReturn(
                new com.example.common.page.PageResult<>(java.util.List.of(), 0, 20, 0L, 0));

        assertThat(queryUseCase.list(new com.wms.master.application.query.ListZonesQuery(
                com.wms.master.application.query.ListZonesCriteria.forWarehouse(WAREHOUSE_ID),
                com.example.common.page.PageQuery.of(0, 20, "updatedAt", "desc"))))
                .isNotNull();
    }

    @Test
    void unauthenticated_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOfAny(AccessDeniedException.class,
                        AuthenticationCredentialsNotFoundException.class);
    }

    private static CreateZoneCommand sampleCreate() {
        return new CreateZoneCommand(WAREHOUSE_ID, "Z-A", "Ambient", ZoneType.AMBIENT, "actor");
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        ZonePersistencePort zonePersistencePort() {
            return mock(ZonePersistencePort.class);
        }

        @Bean
        WarehousePersistencePort warehousePersistencePort() {
            return mock(WarehousePersistencePort.class);
        }

        @Bean
        DomainEventPort eventPort() {
            return mock(DomainEventPort.class);
        }

        @Bean
        ZoneService zoneService(ZonePersistencePort zonePersistencePort,
                                WarehousePersistencePort warehousePersistencePort,
                                DomainEventPort eventPort) {
            return new ZoneService(zonePersistencePort, warehousePersistencePort, eventPort);
        }
    }
}
