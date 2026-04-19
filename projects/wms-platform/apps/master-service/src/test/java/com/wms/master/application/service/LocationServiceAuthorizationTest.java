package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wms.master.application.command.CreateLocationCommand;
import com.wms.master.application.command.DeactivateLocationCommand;
import com.wms.master.application.port.in.LocationCrudUseCase;
import com.wms.master.application.port.in.LocationQueryUseCase;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.LocationPersistencePort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.model.Location;
import com.wms.master.domain.model.LocationType;
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
 * Verifies method-level {@code @PreAuthorize} enforcement on {@link LocationService}.
 * Injection is by use-case interface (not the concrete {@code LocationService}
 * class) because {@code @PreAuthorize} generates a JDK dynamic proxy.
 */
@SpringJUnitConfig(LocationServiceAuthorizationTest.TestConfig.class)
class LocationServiceAuthorizationTest {

    @Autowired
    LocationCrudUseCase crudUseCase;

    @Autowired
    LocationQueryUseCase queryUseCase;

    @Autowired
    LocationPersistencePort locationPersistencePort;

    @Autowired
    ZonePersistencePort zonePersistencePort;

    @Autowired
    WarehousePersistencePort warehousePersistencePort;

    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();

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
        Zone activeZone = Zone.reconstitute(
                ZONE_ID, WAREHOUSE_ID, "Z-A", "Name", ZoneType.AMBIENT,
                com.wms.master.domain.model.WarehouseStatus.ACTIVE, 0L,
                java.time.Instant.now(), "seed",
                java.time.Instant.now(), "seed");
        when(warehousePersistencePort.findById(WAREHOUSE_ID)).thenReturn(Optional.of(activeParent));
        when(zonePersistencePort.findById(ZONE_ID)).thenReturn(Optional.of(activeZone));
        when(locationPersistencePort.insert(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));

        LocationResult result = crudUseCase.create(sampleCreate());
        assertThat(result.locationCode()).isEqualTo("WH01-A-01-01-01");
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_WRITE")
    void deactivate_withWriteRole_isDenied_adminRequired() {
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateLocationCommand(UUID.randomUUID(), "closing", 0L, "actor")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_ADMIN")
    void deactivate_withAdminRole_isAllowed() {
        UUID id = UUID.randomUUID();
        Location loc = Location.create(
                "WH01", WAREHOUSE_ID, ZONE_ID,
                "WH01-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, "actor");
        when(locationPersistencePort.findById(id)).thenReturn(Optional.of(loc));

        // We only care that @PreAuthorize lets us through — downstream version
        // mismatch is exercised elsewhere.
        assertThatThrownBy(() -> crudUseCase.deactivate(
                new DeactivateLocationCommand(id, "closing", 5L, "actor")))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(authorities = "ROLE_MASTER_READ")
    void list_withReadRole_isAllowed() {
        when(locationPersistencePort.findPage(any(), any())).thenReturn(
                new com.example.common.page.PageResult<>(java.util.List.of(), 0, 20, 0L, 0));

        assertThat(queryUseCase.list(new com.wms.master.application.query.ListLocationsQuery(
                com.wms.master.application.query.ListLocationsCriteria.empty(),
                com.example.common.page.PageQuery.of(0, 20, "updatedAt", "desc"))))
                .isNotNull();
    }

    @Test
    void unauthenticated_isDenied() {
        assertThatThrownBy(() -> crudUseCase.create(sampleCreate()))
                .isInstanceOfAny(AccessDeniedException.class,
                        AuthenticationCredentialsNotFoundException.class);
    }

    private static CreateLocationCommand sampleCreate() {
        return new CreateLocationCommand(
                WAREHOUSE_ID, ZONE_ID, "WH01-A-01-01-01",
                null, null, null, null,
                LocationType.STORAGE, null, "actor");
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        LocationPersistencePort locationPersistencePort() {
            return mock(LocationPersistencePort.class);
        }

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
        LocationService locationService(LocationPersistencePort locationPersistencePort,
                                        ZonePersistencePort zonePersistencePort,
                                        WarehousePersistencePort warehousePersistencePort,
                                        DomainEventPort eventPort) {
            return new LocationService(
                    locationPersistencePort, zonePersistencePort,
                    warehousePersistencePort, eventPort);
        }
    }
}
